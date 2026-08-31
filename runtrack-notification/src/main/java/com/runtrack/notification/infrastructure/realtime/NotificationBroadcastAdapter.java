package com.runtrack.notification.infrastructure.realtime;

import com.runtrack.notification.usecases.port.NotificationBroadcaster;
import com.runtrack.notification.usecases.model.inbox.Notification;
import com.runtrack.platform.realtime.LiveChannel;
import com.runtrack.shared.id.UserId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * La poussée temps réel des notifications, sur le canal de {@code platform}.
 *
 * <p>Un sujet par destinataire, là où {@code course} en a un par course : c'est le même transport,
 * et c'est précisément pourquoi il vit dans {@code platform} plutôt qu'en double ici.
 */
@Component
class NotificationBroadcastAdapter implements NotificationBroadcaster {

    private final LiveChannel channel;
    private final NotificationInboxTopic topics;

    NotificationBroadcastAdapter(LiveChannel channel, NotificationInboxTopic topics) {
        this.channel = channel;
        this.topics = topics;
    }

    @Override
    public void deliver(List<Notification> notifications) {
        // Groupé par destinataire, pas une publication par notification : un fan-out en écrit une
        // par abonné, et elles partent vers autant de sujets distincts.
        Map<UserId, List<Notification>> byRecipient = notifications.stream()
                .collect(Collectors.groupingBy(Notification::recipientId));

        byRecipient.forEach((recipient, theirs) -> channel.publish(
                topics.inboxOf(recipient), theirs.stream().map(topics::encode).toList()));
    }
}
