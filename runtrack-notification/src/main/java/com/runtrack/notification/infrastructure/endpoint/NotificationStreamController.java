package com.runtrack.notification.infrastructure.endpoint;

import static com.runtrack.notification.infrastructure.endpoint.NotificationController.requireUser;

import com.runtrack.notification.usecases.service.NotificationInbox;
import com.runtrack.notification.infrastructure.realtime.NotificationInboxTopic;
import com.runtrack.platform.realtime.LiveChannel;
import com.runtrack.platform.realtime.PublishedEvent;
import com.runtrack.platform.openapi.ApiFolders;
import com.runtrack.shared.access.Viewer;
import com.runtrack.shared.id.UserId;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * La pastille qui s'allume sans recharger la page.
 *
 * <p>Même mécanique que le suivi d'une course, même canal : on s'abonne au sujet de sa propre
 * boîte, et l'instantané est la première page de notifications non lues — de quoi peindre le
 * panneau d'un coup, sans un second appel.
 */
@RestController
@ApiFolders.Notifications
@RequestMapping("/notification/v1")
class NotificationStreamController {

    /** Assez pour remplir le panneau déroulant ; au-delà, c'est la boîte complète qu'on ouvre. */
    private static final int SNAPSHOT_SIZE = 20;

    private final NotificationInbox inbox;
    private final NotificationInboxTopic topics;
    private final LiveChannel channel;

    NotificationStreamController(NotificationInbox inbox, NotificationInboxTopic topics,
            LiveChannel channel) {
        this.inbox = inbox;
        this.topics = topics;
        this.channel = channel;
    }

    @Operation(summary = "Recevoir ses notifications en direct (SSE)")
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter follow(
            @AuthenticationPrincipal Viewer viewer,
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId) {

        UserId recipientId = requireUser(viewer);
        return channel.subscribe(
                topics.inboxOf(recipientId),
                Optional.ofNullable(lastEventId),
                () -> snapshotOf(recipientId));
    }

    private List<PublishedEvent> snapshotOf(UserId recipientId) {
        return inbox.page(recipientId, Optional.empty(), true, SNAPSHOT_SIZE).stream()
                .map(topics::encode)
                .toList();
    }
}
