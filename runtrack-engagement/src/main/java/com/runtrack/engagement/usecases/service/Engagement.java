package com.runtrack.engagement.usecases.service;

import com.runtrack.course.CourseApi;
import com.runtrack.engagement.event.ActivityCommented;
import com.runtrack.engagement.event.ActivityLiked;
import com.runtrack.engagement.event.ActivityUnliked;
import com.runtrack.engagement.event.CommentDeleted;
import com.runtrack.engagement.event.CommentReplied;
import com.runtrack.engagement.usecases.port.CommentRepository;
import com.runtrack.engagement.usecases.port.LikeRepository;
import com.runtrack.engagement.usecases.model.interaction.Comment;
import com.runtrack.engagement.usecases.model.interaction.CommentId;
import com.runtrack.engagement.usecases.model.interaction.Like;
import com.runtrack.shared.access.Viewer;
import com.runtrack.shared.context.CallContext;
import com.runtrack.shared.error.ForbiddenException;
import com.runtrack.shared.error.NotFoundException;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aimer et commenter une course.
 *
 * <p>Une seule règle d'accès, et elle n'est pas réécrite ici : {@link CourseApi#canView} tranche
 * (§5.5). Ce module ne sait rien de la visibilité d'un compte ni des abonnements — et c'est
 * précisément ce qui l'empêche de diverger de la politique de {@code course} le jour où celle-ci
 * change.
 *
 * <p>Une course qu'on n'a pas le droit de voir répond « introuvable » et non « interdit » : un 403
 * confirmerait son existence à quelqu'un qui n'était pas censé la connaître.
 */
@Service
public class Engagement {

    /** Assez pour une liste de « j'aime » à l'écran ; au-delà, c'est le compteur qui parle. */
    private static final int MAX_LIKES_LISTED = 100;
    private static final int DEFAULT_COMMENT_PAGE = 20;
    private static final int MAX_COMMENT_PAGE = 100;

    private final LikeRepository likes;
    private final CommentRepository comments;
    private final CourseApi courses;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final RandomGenerator random;

    public Engagement(LikeRepository likes, CommentRepository comments, CourseApi courses,
            ApplicationEventPublisher events, Clock clock, RandomGenerator random) {
        this.likes = likes;
        this.comments = comments;
        this.courses = courses;
        this.events = events;
        this.clock = clock;
        this.random = random;
    }

    @Transactional
    public void like(Viewer viewer, ActivityId activityId) {
        UserId reader = requireVisibleTo(viewer, activityId);
        Instant now = clock.instant();

        // Aimer deux fois est le geste d'un client qui a perdu la réponse, pas une erreur : rien
        // ne se passe, et surtout aucun second événement ne part.
        if (likes.add(new Like(activityId, reader, now))) {
            events.publishEvent(new ActivityLiked(activityId, ownerOf(activityId), reader,
                    likes.countFor(activityId), now, correlationId()));
        }
    }

    @Transactional
    public void unlike(Viewer viewer, ActivityId activityId) {
        UserId reader = requireVisibleTo(viewer, activityId);
        Instant now = clock.instant();

        if (likes.remove(activityId, reader)) {
            events.publishEvent(new ActivityUnliked(
                    activityId, ownerOf(activityId), reader, now, correlationId()));
        }
    }

    @Transactional(readOnly = true)
    public Likes likesOf(Viewer viewer, ActivityId activityId) {
        requireVisible(viewer, activityId);
        return new Likes(
                likes.countFor(activityId),
                likes.ofActivity(activityId, MAX_LIKES_LISTED),
                viewer.userId().map(reader -> likes.exists(activityId, reader)).orElse(false));
    }

    /** @param parentId le commentaire auquel on répond, absent pour un commentaire de premier niveau */
    @Transactional
    public Comment comment(Viewer viewer, ActivityId activityId, String body,
            Optional<CommentId> parentId) {

        UserId author = requireVisibleTo(viewer, activityId);
        Instant now = clock.instant();
        Optional<Comment> parent = parentId.map(id -> requireCommentOn(activityId, id));

        Comment written = comments.save(Comment.written(
                CommentId.generate(clock, random), activityId, author, parentId, body, now));

        // Deux événements distincts parce que les destinataires le sont : une réponse prévient
        // l'auteur du commentaire parent, un commentaire prévient le propriétaire de la course.
        parent.ifPresentOrElse(
                answered -> events.publishEvent(new CommentReplied(activityId, answered.authorId(),
                        author, written.id().toString(), now, correlationId())),
                () -> events.publishEvent(new ActivityCommented(activityId, ownerOf(activityId),
                        author, written.id().toString(), now, correlationId())));
        return written;
    }

    @Transactional
    public Comment edit(Viewer viewer, CommentId id, String body) {
        Comment comment = requireOwnComment(viewer, id);
        return comments.save(comment.editedTo(body, clock.instant()));
    }

    @Transactional
    public void delete(Viewer viewer, CommentId id) {
        Comment comment = requireOwnComment(viewer, id);
        Instant now = clock.instant();
        comments.save(comment.deletedAt(now));
        events.publishEvent(new CommentDeleted(
                comment.activityId(), id.toString(), now, correlationId()));
    }

    @Transactional(readOnly = true)
    public List<Comment> commentsOf(Viewer viewer, ActivityId activityId, Optional<Instant> after,
            Integer limit) {

        requireVisible(viewer, activityId);
        return comments.ofActivity(activityId, after,
                limit == null ? DEFAULT_COMMENT_PAGE : Math.clamp(limit, 1, MAX_COMMENT_PAGE));
    }

    /**
     * Une réponse s'accroche à un commentaire de la <em>même</em> course.
     *
     * <p>Sans cette vérification, un identifiant emprunté ailleurs rattacherait une réponse à un fil
     * qu'on n'a peut-être pas le droit de lire — et ferait fuir, par le simple fait d'accepter, que
     * ce commentaire existe.
     */
    private Comment requireCommentOn(ActivityId activityId, CommentId parentId) {
        Comment parent = comments.findById(parentId).orElseThrow(Engagement::commentNotFound);
        if (!parent.activityId().equals(activityId) || parent.isDeleted()) {
            throw commentNotFound();
        }
        if (parent.isReply()) {
            // Un seul niveau de réponse : au-delà, l'affichage devient un arbre que personne ne
            // sait dessiner sur un téléphone, et le fil perd sa lisibilité.
            throw new com.runtrack.shared.error.ConflictException("COMMENT_NESTING_TOO_DEEP",
                    "On ne répond pas à une réponse");
        }
        return parent;
    }

    private Comment requireOwnComment(Viewer viewer, CommentId id) {
        UserId reader = viewer.userId().orElseThrow(Engagement::commentNotFound);
        Comment comment = comments.findById(id).orElseThrow(Engagement::commentNotFound);
        if (!comment.authorId().equals(reader)) {
            throw commentNotFound();
        }
        return comment;
    }

    private UserId requireVisibleTo(Viewer viewer, ActivityId activityId) {
        requireVisible(viewer, activityId);
        return viewer.userId().orElseThrow(() -> new ForbiddenException("AUTHENTICATION_REQUIRED",
                "Un lien de partage donne à lire, pas à participer"));
    }

    private void requireVisible(Viewer viewer, ActivityId activityId) {
        if (!courses.canView(viewer, activityId)) {
            throw activityNotFound();
        }
    }

    private UserId ownerOf(ActivityId activityId) {
        return courses.ownerOf(activityId).orElseThrow(Engagement::activityNotFound);
    }

    private static NotFoundException activityNotFound() {
        return new NotFoundException("ACTIVITY_NOT_FOUND", "Course introuvable");
    }

    private static NotFoundException commentNotFound() {
        return new NotFoundException("COMMENT_NOT_FOUND", "Commentaire introuvable");
    }

    private static String correlationId() {
        return CallContext.current().map(CallContext::correlationId).orElse("unknown");
    }

    /** Ce que l'écran d'une course affiche des « j'aime » : combien, par qui, et si c'est déjà fait. */
    public record Likes(long total, List<Like> recent, boolean likedByViewer) {
    }
}
