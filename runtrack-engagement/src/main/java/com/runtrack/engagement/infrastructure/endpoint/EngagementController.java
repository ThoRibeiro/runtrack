package com.runtrack.engagement.infrastructure.endpoint;

import com.runtrack.engagement.usecases.service.Engagement;
import com.runtrack.engagement.usecases.model.interaction.Comment;
import com.runtrack.engagement.usecases.model.interaction.CommentId;
import com.runtrack.engagement.usecases.model.interaction.Like;
import com.runtrack.engagement.infrastructure.dto.EngagementDtos;
import com.runtrack.shared.access.Viewer;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Les « j'aime » et les commentaires.
 *
 * <p>Aucune règle de visibilité ici : {@code CourseApi.canView} tranche dans le cas d'usage, comme
 * pour toute autre lecture de course (§5.5).
 */
@RestController
@RequestMapping("/api/v1")
class EngagementController {

    private final Engagement engagement;

    EngagementController(Engagement engagement) {
        this.engagement = engagement;
    }

    @PostMapping("/activities/{id}/likes")
    ResponseEntity<Void> like(@AuthenticationPrincipal Viewer viewer, @PathVariable String id) {
        engagement.like(asViewer(viewer), ActivityId.of(id));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/activities/{id}/likes")
    ResponseEntity<Void> unlike(@AuthenticationPrincipal Viewer viewer, @PathVariable String id) {
        engagement.unlike(asViewer(viewer), ActivityId.of(id));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/activities/{id}/likes")
    EngagementDtos.LikesResponse likes(@AuthenticationPrincipal Viewer viewer, @PathVariable String id) {
        Engagement.Likes found = engagement.likesOf(asViewer(viewer), ActivityId.of(id));
        return new EngagementDtos.LikesResponse(
                found.total(),
                found.recent().stream().map(Like::userId).map(UserId::toString).toList(),
                found.likedByViewer());
    }

    @PostMapping("/activities/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    EngagementDtos.CommentResponse comment(
            @AuthenticationPrincipal Viewer viewer,
            @PathVariable String id,
            @Valid @RequestBody EngagementDtos.PostCommentRequest request) {

        return toResponse(engagement.comment(asViewer(viewer), ActivityId.of(id), request.body(),
                Optional.ofNullable(request.parentId()).map(CommentId::of)));
    }

    @GetMapping("/activities/{id}/comments")
    EngagementDtos.CommentPage comments(
            @AuthenticationPrincipal Viewer viewer,
            @PathVariable String id,
            @RequestParam(required = false) Instant cursor,
            @RequestParam(required = false) Integer limit) {

        List<Comment> page = engagement.commentsOf(
                asViewer(viewer), ActivityId.of(id), Optional.ofNullable(cursor), limit);
        return new EngagementDtos.CommentPage(
                page.stream().map(EngagementController::toResponse).toList(),
                page.isEmpty() ? null : page.getLast().createdAt());
    }

    @PatchMapping("/comments/{id}")
    EngagementDtos.CommentResponse edit(
            @AuthenticationPrincipal Viewer viewer,
            @PathVariable String id,
            @Valid @RequestBody EngagementDtos.EditCommentRequest request) {

        return toResponse(engagement.edit(asViewer(viewer), CommentId.of(id), request.body()));
    }

    @DeleteMapping("/comments/{id}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal Viewer viewer, @PathVariable String id) {
        engagement.delete(asViewer(viewer), CommentId.of(id));
        return ResponseEntity.noContent().build();
    }

    /** Un lecteur absent est un anonyme, pas une erreur : certaines courses sont publiques. */
    private static Viewer asViewer(Viewer viewer) {
        return viewer == null ? Viewer.Anonymous.INSTANCE : viewer;
    }

    private static EngagementDtos.CommentResponse toResponse(Comment comment) {
        return new EngagementDtos.CommentResponse(
                comment.id().toString(),
                comment.activityId().toString(),
                comment.authorId().toString(),
                comment.parentId().map(CommentId::toString).orElse(null),
                // Le texte d'un commentaire supprimé ne ressort pas : la ligne subsiste pour ses
                // réponses, son contenu n'a plus à être lu.
                comment.isDeleted() ? null : comment.body(),
                comment.createdAt(),
                comment.editedAt().orElse(null),
                comment.isDeleted());
    }
}
