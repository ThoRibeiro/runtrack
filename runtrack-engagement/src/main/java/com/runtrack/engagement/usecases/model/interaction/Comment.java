package com.runtrack.engagement.usecases.model.interaction;

import com.runtrack.shared.error.ConflictException;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Un commentaire sous une course, éventuellement en réponse à un autre.
 *
 * <p><b>Trente minutes pour se corriger, pas plus.</b> Passé ce délai, d'autres ont répondu et
 * l'échange n'aurait plus de sens si l'on pouvait réécrire ce à quoi ils répondaient. La fenêtre
 * court depuis l'écriture et non depuis la dernière modification : sinon une suite de corrections
 * la ferait glisser indéfiniment.
 *
 * <p><b>Suppression logique.</b> Un commentaire effacé pour de bon emporterait les réponses qui s'y
 * accrochent, et laisserait des trous dans un fil de discussion. Il reste donc en base, vidé de son
 * texte à l'affichage, et ne peut plus être modifié.
 */
public record Comment(
        CommentId id,
        ActivityId activityId,
        UserId authorId,
        Optional<CommentId> parentId,
        String body,
        Instant createdAt,
        Optional<Instant> editedAt,
        Optional<Instant> deletedAt) {

    public static final int MAX_LENGTH = 1_000;
    public static final Duration EDIT_WINDOW = Duration.ofMinutes(30);

    public Comment {
        if (id == null || activityId == null || authorId == null || parentId == null
                || createdAt == null || editedAt == null || deletedAt == null) {
            throw new IllegalArgumentException("Commentaire incomplet");
        }
        body = requireUsableBody(body);
    }

    public static Comment written(CommentId id, ActivityId activityId, UserId authorId,
            Optional<CommentId> parentId, String body, Instant createdAt) {

        return new Comment(id, activityId, authorId, parentId, body, createdAt,
                Optional.empty(), Optional.empty());
    }

    public boolean isDeleted() {
        return deletedAt.isPresent();
    }

    public boolean isReply() {
        return parentId.isPresent();
    }

    public boolean isEditableAt(Instant moment) {
        return !isDeleted() && moment.isBefore(createdAt.plus(EDIT_WINDOW));
    }

    public Comment editedTo(String newBody, Instant moment) {
        if (isDeleted()) {
            throw new ConflictException("COMMENT_DELETED", "Un commentaire supprimé ne se modifie plus");
        }
        if (!isEditableAt(moment)) {
            throw new ConflictException("COMMENT_EDIT_WINDOW_CLOSED",
                    "Un commentaire ne se modifie plus après " + EDIT_WINDOW.toMinutes() + " minutes");
        }
        return new Comment(id, activityId, authorId, parentId, newBody, createdAt,
                Optional.of(moment), deletedAt);
    }

    /** Supprimer deux fois ne déplace pas la date : c'est le premier geste qui compte. */
    public Comment deletedAt(Instant moment) {
        return isDeleted() ? this : new Comment(id, activityId, authorId, parentId, body,
                createdAt, editedAt, Optional.of(moment));
    }

    private static String requireUsableBody(String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Un commentaire vide n'a rien à dire");
        }
        String trimmed = body.strip();
        if (trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Commentaire limité à " + MAX_LENGTH + " caractères, reçu " + trimmed.length());
        }
        return trimmed;
    }
}
