package com.runtrack.notification.internal.application;

import com.runtrack.notification.internal.application.port.NotificationPreferencesRepository;
import com.runtrack.notification.internal.domain.inbox.NotificationPreferences;
import com.runtrack.notification.internal.domain.inbox.NotificationType;
import com.runtrack.notification.internal.domain.push.QuietHours;
import com.runtrack.shared.id.UserId;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ce que le destinataire choisit de recevoir. */
@Service
public class NotificationSettings {

    private final NotificationPreferencesRepository preferences;

    public NotificationSettings(NotificationPreferencesRepository preferences) {
        this.preferences = preferences;
    }

    /** Un compte qui n'a jamais rien réglé reçoit tout : l'absence de ligne est un état valide. */
    @Transactional(readOnly = true)
    public NotificationPreferences of(UserId userId) {
        return preferences.find(userId).orElseGet(() -> NotificationPreferences.everythingOn(userId));
    }

    /**
     * Remplace l'ensemble des réglages, plutôt que d'en modifier un.
     *
     * <p>C'est l'écran de préférences qui appelle : il connaît l'état complet, et un remplacement
     * ne peut pas dériver — là où un ajout et un retrait finissent toujours par se croiser.
     */
    @Transactional
    public NotificationPreferences update(UserId userId, Set<NotificationType> muted,
            Optional<QuietHours> quietHours) {

        NotificationPreferences updated = NotificationPreferences.everythingOn(userId)
                .mute(muted)
                .quietBetween(quietHours);
        preferences.save(updated);
        return updated;
    }
}
