package com.runtrack.engagement.internal.infra.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Les contrats HTTP de {@code engagement}. */
public final class EngagementDtos {

    private EngagementDtos() {
    }

    /** @param parentId le commentaire auquel on répond ; absent pour un commentaire de la course */
    public record PostCommentRequest(
            @NotBlank @Size(max = 1_000) String body,
            String parentId) {
    }

    public record EditCommentRequest(@NotBlank @Size(max = 1_000) String body) {
    }

    /**
     * @param body {@code null} pour un commentaire supprimé — la ligne reste, pour que les
     *     réponses qui s'y accrochent gardent leur place, mais son texte ne ressort plus
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CommentResponse(
            String id,
            String activityId,
            String authorId,
            String parentId,
            String body,
            Instant createdAt,
            Instant editedAt,
            boolean deleted) {
    }

    public record CommentPage(List<CommentResponse> items, Instant nextCursor) {
    }

    /** @param likedByViewer de quoi allumer le cœur sans une seconde requête */
    public record LikesResponse(long total, List<String> recentUserIds, boolean likedByViewer) {
    }
}
