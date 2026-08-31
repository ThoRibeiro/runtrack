package com.runtrack.engagement.usecases.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.runtrack.course.ActivitySummary;
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
import com.runtrack.shared.error.ConflictException;
import com.runtrack.shared.error.ForbiddenException;
import com.runtrack.shared.error.NotFoundException;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/** Aimer et commenter : la règle d'accès, l'unicité, et ce qui part en événement. */
class EngagementTest {

    private static final ActivityId RUN = new ActivityId(UUID.fromString("018f4c1e-0000-7000-8000-0000000000ff"));
    private static final UserId MARIE = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000001"));
    private static final UserId PAUL = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000002"));
    private static final Viewer READER = new Viewer.AuthenticatedUser(PAUL);
    private static final Instant NOON = Instant.parse("2026-08-31T12:00:00Z");

    private static final class Likes implements LikeRepository {

        private final Set<String> stored = new LinkedHashSet<>();
        private final List<Like> ordered = new ArrayList<>();

        @Override
        public boolean add(Like like) {
            if (!stored.add(like.activityId() + "|" + like.userId())) {
                return false;
            }
            ordered.add(like);
            return true;
        }

        @Override
        public boolean remove(ActivityId activityId, UserId userId) {
            ordered.removeIf(like -> like.activityId().equals(activityId)
                    && like.userId().equals(userId));
            return stored.remove(activityId + "|" + userId);
        }

        @Override
        public boolean exists(ActivityId activityId, UserId userId) {
            return stored.contains(activityId + "|" + userId);
        }

        @Override
        public long countFor(ActivityId activityId) {
            return ordered.stream().filter(like -> like.activityId().equals(activityId)).count();
        }

        @Override
        public List<Like> ofActivity(ActivityId activityId, int limit) {
            return ordered.stream().filter(like -> like.activityId().equals(activityId))
                    .limit(limit).toList();
        }
    }

    private static final class Comments implements CommentRepository {

        private final Map<CommentId, Comment> stored = new LinkedHashMap<>();

        @Override
        public Comment save(Comment comment) {
            stored.put(comment.id(), comment);
            return comment;
        }

        @Override
        public Optional<Comment> findById(CommentId id) {
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public List<Comment> ofActivity(ActivityId activityId, Optional<Instant> after, int limit) {
            return stored.values().stream()
                    .filter(comment -> comment.activityId().equals(activityId))
                    .filter(comment -> after.map(comment.createdAt()::isAfter).orElse(true))
                    .sorted(Comparator.comparing(Comment::createdAt))
                    .limit(limit)
                    .toList();
        }

        @Override
        public long countFor(ActivityId activityId) {
            return stored.values().stream()
                    .filter(comment -> comment.activityId().equals(activityId))
                    .filter(comment -> !comment.isDeleted())
                    .count();
        }
    }

    /** La visibilité est pilotée à la main : c'est la seule chose que le test fait varier. */
    private static final class Courses implements CourseApi {

        private boolean visible = true;

        @Override
        public Optional<ActivitySummary> summary(ActivityId activityId) {
            return Optional.empty();
        }

        @Override
        public Map<ActivityId, ActivitySummary> summaries(Collection<ActivityId> activityIds) {
            return Map.of();
        }

        @Override
        public Optional<UserId> ownerOf(ActivityId activityId) {
            return Optional.of(MARIE);
        }

        @Override
        public boolean canView(Viewer viewer, ActivityId activityId) {
            return visible;
        }
    }

    private Likes likes;
    private Comments comments;
    private Courses courses;
    private List<Object> published;
    private Engagement engagement;

    @BeforeEach
    void setUp() {
        likes = new Likes();
        comments = new Comments();
        courses = new Courses();
        published = new ArrayList<>();
        ApplicationEventPublisher events = published::add;
        engagement = new Engagement(likes, comments, courses, events,
                Clock.fixed(NOON, ZoneOffset.UTC), new Random(3));
    }

    @Test
    void likingPublishesTheEventWithTheRunningTotal() {
        engagement.like(READER, RUN);

        assertThat(published).singleElement().isInstanceOfSatisfying(ActivityLiked.class, liked -> {
            assertThat(liked.ownerId()).isEqualTo(MARIE);
            assertThat(liked.likerId()).isEqualTo(PAUL);
            assertThat(liked.likeCount()).isEqualTo(1);
        });
    }

    /** Aimer deux fois est un clic renvoyé, pas un second fait : rien ne repart. */
    @Test
    void likingTwicePublishesNothingMore() {
        engagement.like(READER, RUN);
        engagement.like(READER, RUN);

        assertThat(published).hasSize(1);
        assertThat(likes.countFor(RUN)).isEqualTo(1);
    }

    @Test
    void unlikingPublishesAndLowersTheCount() {
        engagement.like(READER, RUN);
        engagement.unlike(READER, RUN);

        assertThat(published).last().isInstanceOf(ActivityUnliked.class);
        assertThat(likes.countFor(RUN)).isZero();
    }

    @Test
    void unlikingWhatWasNotLikedPublishesNothing() {
        engagement.unlike(READER, RUN);

        assertThat(published).isEmpty();
    }

    /** §5.5 : la règle d'accès n'est pas réécrite ici, et une course invisible est introuvable. */
    @Test
    void whatCannotBeSeenCannotBeLiked() {
        courses.visible = false;

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> engagement.like(READER, RUN));
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> engagement.commentsOf(READER, RUN, Optional.empty(), null));
    }

    /** Un lien de partage donne à lire, pas à participer. */
    @Test
    void aShareLinkHolderCannotLike() {
        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> engagement.like(new Viewer.ShareLinkHolder(RUN), RUN));
    }

    @Test
    void aCommentPublishesActivityCommented() {
        engagement.comment(READER, RUN, "Bravo", Optional.empty());

        assertThat(published).singleElement().isInstanceOfSatisfying(
                ActivityCommented.class, posted -> assertThat(posted.ownerId()).isEqualTo(MARIE));
    }

    /** Une réponse prévient l'auteur du commentaire parent, pas le propriétaire de la course. */
    @Test
    void aReplyPublishesCommentRepliedTowardsTheParentAuthor() {
        Comment parent = engagement.comment(READER, RUN, "Bravo", Optional.empty());
        published.clear();

        engagement.comment(new Viewer.AuthenticatedUser(MARIE), RUN, "Merci",
                Optional.of(parent.id()));

        assertThat(published).singleElement().isInstanceOfSatisfying(
                CommentReplied.class, replied -> assertThat(replied.parentAuthorId()).isEqualTo(PAUL));
    }

    /** Un seul niveau : un fil qu'on ne sait pas dessiner sur un téléphone n'est plus un fil. */
    @Test
    void nobodyRepliesToAReply() {
        Comment parent = engagement.comment(READER, RUN, "Bravo", Optional.empty());
        Comment reply = engagement.comment(READER, RUN, "Merci", Optional.of(parent.id()));

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> engagement.comment(READER, RUN, "Encore", Optional.of(reply.id())));
    }

    /** Un identifiant emprunté ailleurs ne rattache rien : ce serait une fuite par acceptation. */
    @Test
    void aReplyCannotHookOntoACommentOfAnotherRun() {
        Comment elsewhere = engagement.comment(READER,
                new ActivityId(UUID.randomUUID()), "Ailleurs", Optional.empty());

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> engagement.comment(READER, RUN, "Ici", Optional.of(elsewhere.id())));
    }

    @Test
    void onlyTheAuthorEditsOrDeletesTheirComment() {
        Comment comment = engagement.comment(READER, RUN, "Bravo", Optional.empty());
        Viewer someoneElse = new Viewer.AuthenticatedUser(MARIE);

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> engagement.edit(someoneElse, comment.id(), "Autre"));
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> engagement.delete(someoneElse, comment.id()));
    }

    @Test
    void deletingPublishesSoTheCounterCanGoDown() {
        Comment comment = engagement.comment(READER, RUN, "Bravo", Optional.empty());
        published.clear();

        engagement.delete(READER, comment.id());

        assertThat(published).singleElement().isInstanceOf(CommentDeleted.class);
        assertThat(comments.countFor(RUN)).isZero();
    }

    @Test
    void theLikesOfARunTellWhetherTheViewerIsAmongThem() {
        engagement.like(READER, RUN);

        Engagement.Likes found = engagement.likesOf(READER, RUN);

        assertThat(found.total()).isEqualTo(1);
        assertThat(found.likedByViewer()).isTrue();
        assertThat(engagement.likesOf(Viewer.Anonymous.INSTANCE, RUN).likedByViewer()).isFalse();
    }
}
