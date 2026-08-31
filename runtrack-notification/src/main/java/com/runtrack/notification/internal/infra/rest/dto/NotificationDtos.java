package com.runtrack.notification.internal.infra.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Les contrats HTTP de {@code notification}. */
public final class NotificationDtos {

    private NotificationDtos() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NotificationResponse(
            String id,
            String type,
            String actorId,
            String deepLink,
            Instant createdAt,
            Instant readAt,
            boolean unread) {
    }

    /** Pagination par curseur : le client renvoie {@code nextCursor} pour la page suivante. */
    public record NotificationPage(List<NotificationResponse> items, Instant nextCursor) {
    }

    public record UnreadCountResponse(long unread) {
    }

    public record MarkAllReadResponse(int marked) {
    }

    /**
     * Les préférences, exprimées en natures coupées.
     *
     * <p>Le PATCH remplace la liste entière plutôt que d'ajouter ou retirer : c'est l'écran de
     * réglages qui l'envoie, il connaît l'état complet, et un remplacement ne peut pas dériver.
     */
    public record PreferencesRequest(@NotNull Set<String> muted) {
    }

    public record PreferencesResponse(Set<String> muted, Set<String> available) {
    }
}
