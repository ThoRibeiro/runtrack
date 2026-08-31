package com.runtrack.notification.infrastructure.endpoint;

import com.runtrack.notification.usecases.service.NotificationInbox;
import com.runtrack.notification.usecases.model.inbox.Notification;
import com.runtrack.notification.usecases.model.inbox.NotificationId;
import com.runtrack.notification.infrastructure.dto.NotificationDtos;
import com.runtrack.shared.access.Viewer;
import com.runtrack.shared.error.ForbiddenException;
import com.runtrack.shared.id.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * La boîte de réception.
 *
 * <p>Aucun identifiant de destinataire dans les chemins : on ne lit que la sienne, et le seul
 * moyen de la désigner est d'être connecté. Un {@code /users/{id}/notifications} ouvrirait une
 * question d'autorisation qui n'a aucune raison d'exister.
 */
@RestController
@RequestMapping("/api/v1")
class NotificationController {

    private final NotificationInbox inbox;

    NotificationController(NotificationInbox inbox) {
        this.inbox = inbox;
    }

    @GetMapping("/notifications")
    NotificationDtos.NotificationPage list(
            @AuthenticationPrincipal Viewer viewer,
            @RequestParam(required = false) Instant cursor,
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) Integer limit) {

        List<Notification> page = inbox.page(
                requireUser(viewer), Optional.ofNullable(cursor), unreadOnly, limit);
        return new NotificationDtos.NotificationPage(
                page.stream().map(NotificationMapper::toResponse).toList(),
                NotificationMapper.nextCursorOf(page));
    }

    @GetMapping("/notifications/unread-count")
    NotificationDtos.UnreadCountResponse unreadCount(@AuthenticationPrincipal Viewer viewer) {
        return new NotificationDtos.UnreadCountResponse(inbox.unreadCount(requireUser(viewer)));
    }

    @PostMapping("/notifications/{id}/read")
    ResponseEntity<Void> markRead(@AuthenticationPrincipal Viewer viewer, @PathVariable String id) {
        inbox.markRead(requireUser(viewer), NotificationId.of(id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/notifications/read-all")
    NotificationDtos.MarkAllReadResponse markAllRead(@AuthenticationPrincipal Viewer viewer) {
        return new NotificationDtos.MarkAllReadResponse(inbox.markAllRead(requireUser(viewer)));
    }

    /** Une boîte de réception appartient à un compte : ni l'anonyme ni un lien de partage n'en ont. */
    static UserId requireUser(Viewer viewer) {
        if (viewer == null) {
            throw new ForbiddenException("AUTHENTICATION_REQUIRED", "Cette action demande d'être connecté");
        }
        return viewer.userId().orElseThrow(() -> new ForbiddenException(
                "AUTHENTICATION_REQUIRED", "Un lien de partage n'a pas de boîte de réception"));
    }
}
