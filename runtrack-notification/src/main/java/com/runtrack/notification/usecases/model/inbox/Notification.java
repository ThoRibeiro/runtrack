package com.runtrack.notification.usecases.model.inbox;

import com.runtrack.shared.id.UserId;
import java.time.Instant;
import java.util.Optional;

/**
 * Une notification dans la boîte d'un destinataire.
 *
 * <p>Un fait, pas un agrégat : une fois écrite, seule sa lecture change. D'où un record, et une
 * marque de lecture qui rend une nouvelle instance plutôt que de muter celle-ci.
 *
 * <p>{@code aggregateCount} est le nombre de faits qu'elle résume. Il vaut un pour tout ce qui est
 * unique — un nouvel abonné, une course démarrée — et grandit pour ce qui se répète sur le même
 * sujet, les « j'aime » en tête : vingt personnes qui aiment la même course, ce sont vingt
 * interruptions pour un seul fait, et une boîte où le reste devient illisible.
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
        Optional<Instant> readAt,
        int aggregateCount) {

    public Notification {
        if (id == null || recipientId == null || type == null || actorId == null
                || deepLink == null || createdAt == null || readAt == null) {
            throw new IllegalArgumentException("Notification incomplète");
        }
        if (aggregateCount < 1) {
            throw new IllegalArgumentException("Une notification résume au moins un fait");
        }
        if (deepLink.isBlank()) {
            throw new IllegalArgumentException("Une notification sans destination n'a pas d'usage");
        }
    }

    public static Notification unread(NotificationId id, UserId recipientId, NotificationType type,
            UserId actorId, String deepLink, Instant createdAt) {

        return new Notification(id, recipientId, type, Optional.ofNullable(actorId),
                deepLink, createdAt, Optional.empty(), 1);
    }

    public boolean isUnread() {
        return readAt.isEmpty();
    }

    /** Relire une notification déjà lue ne déplace pas sa date de lecture. */
    public Notification readAt(Instant when) {
        return isUnread() ? new Notification(
                id, recipientId, type, actorId, deepLink, createdAt, Optional.of(when),
                aggregateCount) : this;
    }
}
