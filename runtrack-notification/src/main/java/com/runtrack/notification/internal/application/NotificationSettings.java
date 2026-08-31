package com.runtrack.notification.internal.application;

import com.runtrack.notification.internal.application.port.NotificationPreferencesRepository;
import com.runtrack.notification.internal.domain.inbox.NotificationPreferences;
import com.runtrack.notification.internal.domain.inbox.NotificationType;
import com.runtrack.shared.id.UserId;
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

    @Transactional
    public NotificationPreferences mute(UserId userId, Set<NotificationType> types) {
        NotificationPreferences updated = NotificationPreferences.everythingOn(userId).mute(types);
        preferences.save(updated);
        return updated;
    }
}
