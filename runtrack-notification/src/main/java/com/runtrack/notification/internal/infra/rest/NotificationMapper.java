package com.runtrack.notification.internal.infra.rest;

import com.runtrack.notification.internal.domain.inbox.Notification;
import com.runtrack.notification.internal.infra.rest.dto.NotificationDtos;
import com.runtrack.shared.id.UserId;
import java.time.Instant;

/**
 * Notification vers DTO, à la main.
 *
 * <p>Public parce que la diffusion temps réel s'en sert aussi : ce qu'un client reçoit sur son
 * flux SSE doit être exactement ce que lui rend {@code GET /notifications}, sans quoi il
 * écrirait deux fois le même affichage.
 */
public final class NotificationMapper {

    private NotificationMapper() {
    }

    public static NotificationDtos.NotificationResponse toResponse(Notification notification) {
        return new NotificationDtos.NotificationResponse(
                notification.id().toString(),
                notification.type().name(),
                notification.actorId().map(UserId::toString).orElse(null),
                notification.deepLink(),
                notification.createdAt(),
                notification.readAt().orElse(null),
                notification.isUnread());
    }

    static Instant nextCursorOf(java.util.List<Notification> page) {
        return page.isEmpty() ? null : page.getLast().createdAt();
    }
}
