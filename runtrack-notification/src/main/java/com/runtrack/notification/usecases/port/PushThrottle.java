package com.runtrack.notification.usecases.port;

import com.runtrack.notification.usecases.model.inbox.NotificationType;
import com.runtrack.shared.id.UserId;
import java.time.Duration;

/**
 * Le garde-fou anti-spam du §7 : au plus un push par couple (acteur, destinataire) et par nature
 * sur une fenêtre glissante.
 *
 * <p>Il vit dans Dragonfly plutôt qu'en base : c'est une donnée qui expire d'elle-même, que
 * personne ne relit jamais, et dont la perte n'a d'autre conséquence qu'un push de trop.
 */
public interface PushThrottle {

    /**
     * @return {@code true} si ce push peut partir — et, dans ce cas seulement, la fenêtre est
     *     armée. Deux appels concurrents ne peuvent pas obtenir {@code true} tous les deux.
     */
    boolean allow(UserId actorId, UserId recipientId, NotificationType type, Duration window);
}
