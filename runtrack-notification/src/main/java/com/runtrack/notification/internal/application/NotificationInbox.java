package com.runtrack.notification.internal.application;

import com.runtrack.notification.internal.application.port.NotificationRepository;
import com.runtrack.notification.internal.domain.inbox.Notification;
import com.runtrack.notification.internal.domain.inbox.NotificationId;
import com.runtrack.shared.error.NotFoundException;
import com.runtrack.shared.id.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La boîte de réception vue par son propriétaire.
 *
 * <p>Chaque méthode prend le destinataire et le passe jusqu'à la requête, plutôt que de charger
 * une notification puis de comparer. Une notification qui n'est pas la vôtre est introuvable, et
 * elle l'est parce que la requête ne la ramène pas — pas parce qu'un {@code if} y a pensé.
 */
@Service
public class NotificationInbox {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository notifications;
    private final Clock clock;

    public NotificationInbox(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Notification> page(UserId recipientId, Optional<Instant> before, boolean unreadOnly,
            Integer limit) {

        return notifications.findFor(recipientId, before, unreadOnly, pageSize(limit));
    }

    @Transactional(readOnly = true)
    public long unreadCount(UserId recipientId) {
        return notifications.unreadCount(recipientId);
    }

    @Transactional
    public void markRead(UserId recipientId, NotificationId id) {
        if (!notifications.markRead(recipientId, id, clock.instant())
                && notifications.find(recipientId, id).isEmpty()) {
            throw new NotFoundException("NOTIFICATION_NOT_FOUND", "Notification introuvable");
        }
    }

    /** @return le nombre de notifications que cet appel a fait passer en lu */
    @Transactional
    public int markAllRead(UserId recipientId) {
        return notifications.markAllRead(recipientId, clock.instant());
    }

    private static int pageSize(Integer requested) {
        return requested == null ? DEFAULT_PAGE_SIZE : Math.clamp(requested, 1, MAX_PAGE_SIZE);
    }
}
