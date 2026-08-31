package com.runtrack.notification.internal.domain.inbox;

import com.runtrack.shared.id.UserId;
import java.time.Instant;
import java.util.Optional;

/**
 * Une notification dans la boîte d'un destinataire.
 *
 * <p>Un fait, pas un agrégat : une fois écrite, seule sa lecture change. D'où un record, et une
 * marque de lecture qui rend une nouvelle instance plutôt que de muter celle-ci.
 *
 * <p>Elle ne porte ni titre ni texte. Le libellé — « Marie vient de démarrer une course » — se
 * compose à l'affichage, à partir de la nature et de l'acteur : le stocker figerait la langue du
 * destinataire au moment de l'écriture, et rendrait impossible de corriger une formulation
 * autrement qu'en réécrivant l'historique.
 */
public record Notification(
        NotificationId id,
        UserId recipientId,
        NotificationType type,
        Optional<UserId> actorId,
        String deepLink,
        Instant createdAt,
        Optional<Instant> readAt) {

    public Notification {
        if (id == null || recipientId == null || type == null || actorId == null
                || deepLink == null || createdAt == null || readAt == null) {
            throw new IllegalArgumentException("Notification incomplète");
        }
        if (deepLink.isBlank()) {
            throw new IllegalArgumentException("Une notification sans destination n'a pas d'usage");
        }
    }

    public static Notification unread(NotificationId id, UserId recipientId, NotificationType type,
            UserId actorId, String deepLink, Instant createdAt) {

        return new Notification(id, recipientId, type, Optional.ofNullable(actorId),
                deepLink, createdAt, Optional.empty());
    }

    public boolean isUnread() {
        return readAt.isEmpty();
    }

    /** Relire une notification déjà lue ne déplace pas sa date de lecture. */
    public Notification readAt(Instant when) {
        return isUnread() ? new Notification(
                id, recipientId, type, actorId, deepLink, createdAt, Optional.of(when)) : this;
    }
}
