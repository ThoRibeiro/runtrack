package com.runtrack.notification.internal.infra.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalTime;
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
    public record PreferencesRequest(@NotNull Set<String> muted, @Valid QuietHoursDto quietHours) {
    }

    public record PreferencesResponse(
            Set<String> muted, Set<String> available, QuietHoursDto quietHours) {
    }

    /**
     * Les heures calmes : trois champs qui vont ensemble, ou {@code null} pour n'en avoir aucune.
     *
     * <p>Le fuseau est obligatoire dès qu'une plage est donnée. « Pas avant 7 h » n'a de sens que
     * là où se trouve le destinataire, et le déduire du serveur réveillerait un coureur de Nouméa
     * à ce qui est 7 h à Paris.
     */
    public record QuietHoursDto(
            @NotNull LocalTime from,
            @NotNull LocalTime to,
            @NotBlank String zone) {
    }

    public record RegisterDeviceRequest(
            @NotBlank @Size(max = 512) String token,
            @NotBlank String platform) {
    }

    public record DeviceResponse(String token, String platform, Instant registeredAt) {
    }

    public record DeviceListResponse(List<DeviceResponse> items) {
    }
}
