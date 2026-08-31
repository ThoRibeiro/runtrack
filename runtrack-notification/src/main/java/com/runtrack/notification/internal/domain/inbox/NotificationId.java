package com.runtrack.notification.internal.domain.inbox;

import com.runtrack.shared.id.UserId;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * L'identifiant d'une notification, <b>déduit</b> de ce qui l'a provoquée.
 *
 * <p>C'est là que se joue l'idempotence exigée au §7. Le registre de Modulith rejoue au
 * redémarrage tout ce qui n'avait pas abouti, et un rejeu ne doit pas notifier deux fois. Un
 * identifiant tiré au sort donnerait deux lignes ; un identifiant calculé sur
 * (nature, destinataire, sujet, instant de l'événement) donne deux fois le même, et l'insertion
 * en base ne garde que la première.
 *
 * <p>L'instant vient de l'<em>événement</em>, pas de l'horloge : c'est ce qui rend un rejeu
 * identique tout en laissant deux faits distincts se distinguer. Se réabonner à quelqu'un après
 * s'être désabonné produit bien une seconde notification, parce que c'est un second événement.
 */
public record NotificationId(UUID value) {

    public NotificationId {
        if (value == null) {
            throw new IllegalArgumentException("NotificationId requiert une valeur");
        }
    }

    public static NotificationId of(String value) {
        try {
            return new NotificationId(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("NotificationId invalide : " + value, e);
        }
    }

    /**
     * @param subject ce sur quoi porte la notification — une course, un compte — ou {@code null}
     *     quand la nature et le destinataire suffisent à l'identifier
     */
    public static NotificationId deducedFrom(NotificationType type, UserId recipientId,
            String subject, Instant occurredAt) {

        if (type == null || recipientId == null || occurredAt == null) {
            throw new IllegalArgumentException("Identifiant de notification indéductible");
        }
        String seed = String.join("|", type.name(), recipientId.toString(),
                subject == null ? "" : subject, occurredAt.toString());
        return new NotificationId(UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
