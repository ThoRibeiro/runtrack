package com.runtrack.notification.infrastructure.realtime;

import com.runtrack.notification.usecases.model.inbox.Notification;
import com.runtrack.notification.infrastructure.endpoint.NotificationMapper;
import com.runtrack.platform.realtime.PublishedEvent;
import com.runtrack.shared.id.UserId;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Le sujet d'une boîte de réception, et la forme sous laquelle ses notifications voyagent.
 *
 * <p>Les deux au même endroit parce qu'ils vont toujours ensemble : celui qui pousse et celui qui
 * ouvre le flux doivent nommer le même sujet et produire le même JSON, faute de quoi un client
 * recevrait en direct autre chose que ce que sa boîte lui rendra.
 *
 * <p>La charge utile est celle de l'API REST, à l'identique : le client écrit un seul affichage.
 */
@Component
public class NotificationInboxTopic {

    static final String EVENT_KIND = "notification";

    private final ObjectMapper json;

    NotificationInboxTopic(ObjectMapper json) {
        this.json = json;
    }

    public String inboxOf(UserId recipientId) {
        return "live:user:" + recipientId + ":notifications";
    }

    public PublishedEvent encode(Notification notification) {
        return PublishedEvent.withoutId(
                EVENT_KIND, json.writeValueAsString(NotificationMapper.toResponse(notification)));
    }
}
