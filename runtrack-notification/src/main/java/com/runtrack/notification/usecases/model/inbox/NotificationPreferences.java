package com.runtrack.notification.usecases.model.inbox;

import com.runtrack.notification.usecases.model.push.QuietHours;
import com.runtrack.shared.id.UserId;
import java.util.EnumSet;
import java.util.Optional;
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
 *
 * <p>Les heures calmes ne coupent que le <b>push</b>. Une notification reste écrite dans la boîte :
 * elle sera là au réveil. Ce qu'on suspend, c'est l'interruption, pas l'information.
 */
public record NotificationPreferences(
        UserId userId, Set<NotificationType> muted, Optional<QuietHours> quietHours) {

    public NotificationPreferences {
        if (userId == null || muted == null || quietHours == null) {
            throw new IllegalArgumentException("Préférences incomplètes");
        }
        muted = muted.isEmpty() ? EnumSet.noneOf(NotificationType.class) : EnumSet.copyOf(muted);
    }

    public static NotificationPreferences everythingOn(UserId userId) {
        return new NotificationPreferences(
                userId, EnumSet.noneOf(NotificationType.class), Optional.empty());
    }

    public boolean allows(NotificationType type) {
        return !muted.contains(type);
    }

    /** Le push peut-il partir maintenant, ou le destinataire dort-il ? */
    public boolean acceptsPushAt(java.time.Instant moment) {
        return quietHours.map(hours -> !hours.covers(moment)).orElse(true);
    }

    public NotificationPreferences mute(Set<NotificationType> types) {
        return new NotificationPreferences(userId, types, quietHours);
    }

    public NotificationPreferences quietBetween(Optional<QuietHours> hours) {
        return new NotificationPreferences(userId, muted, hours);
    }
}
