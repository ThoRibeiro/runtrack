package com.runtrack.notification.internal.domain.inbox;

import com.runtrack.shared.id.UserId;
import java.util.EnumSet;
import java.util.Set;

/**
 * Ce qu'un destinataire a choisi de ne plus recevoir.
 *
 * <p>On stocke les natures <b>coupées</b>, jamais celles activées. La différence n'apparaît qu'au
 * moment d'en ajouter une : avec une liste d'activées, un type nouveau arrive éteint chez tout le
 * monde et personne ne le découvre ; avec une liste de coupées, il arrive allumé — ce qui est le
 * comportement attendu d'une fonctionnalité qu'on vient de livrer, et que chacun reste libre de
 * couper.
 *
 * <p>Absence de préférences enregistrées et « rien de coupé » sont donc le même état, ce qui évite
 * d'écrire une ligne en base pour chaque compte créé.
 */
public record NotificationPreferences(UserId userId, Set<NotificationType> muted) {

    public NotificationPreferences {
        if (userId == null || muted == null) {
            throw new IllegalArgumentException("Préférences incomplètes");
        }
        muted = muted.isEmpty() ? EnumSet.noneOf(NotificationType.class) : EnumSet.copyOf(muted);
    }

    public static NotificationPreferences everythingOn(UserId userId) {
        return new NotificationPreferences(userId, EnumSet.noneOf(NotificationType.class));
    }

    public boolean allows(NotificationType type) {
        return !muted.contains(type);
    }

    public NotificationPreferences mute(Set<NotificationType> types) {
        return new NotificationPreferences(userId, types);
    }
}
