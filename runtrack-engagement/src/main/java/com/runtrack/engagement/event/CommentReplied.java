package com.runtrack.engagement.event;

import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import java.time.Instant;

/**
 * Une réponse à un commentaire.
 *
 * <p>Distinct d'{@code ActivityCommented} parce que le destinataire n'est pas le même : c'est
 * l'auteur du commentaire parent qu'on prévient, pas celui de la course.
 */
public record CommentReplied(
        ActivityId activityId, UserId parentAuthorId, UserId authorId, String commentId,
        Instant at, String correlationId) {
}
