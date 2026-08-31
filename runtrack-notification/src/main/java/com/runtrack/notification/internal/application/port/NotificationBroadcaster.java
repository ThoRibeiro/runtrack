package com.runtrack.notification.internal.application.port;

import com.runtrack.notification.internal.domain.inbox.Notification;
import java.util.List;

/**
 * La poussée temps réel vers les destinataires connectés.
 *
 * <p>Comme le direct d'une course, elle ne doit jamais faire échouer l'appelant : la notification
 * est en base, le destinataire la verra en rouvrant l'application. Une pastille qui met une
 * seconde de plus à apparaître n'a pas la même gravité qu'une notification perdue.
 */
public interface NotificationBroadcaster {

    void deliver(List<Notification> notifications);
}
