package com.runtrack.course.internal.infra.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Les contrats HTTP de {@code course}. */
public final class ActivityDtos {

    private ActivityDtos() {
    }

    public record StartActivityRequest(
            @NotBlank String type,
            @NotBlank @Size(max = 120) String title,
            @Size(max = 2_000) String description,
            @NotBlank String visibility,
            /** L'heure du téléphone, pour mesurer sa dérive une fois pour toute la course. */
            Instant deviceTime) {
    }

    public record UpdateActivityRequest(
            @NotBlank @Size(max = 120) String title,
            @Size(max = 2_000) String description) {
    }

    public record ChangeVisibilityRequest(@NotBlank String visibility) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ActivityResponse(
            String id,
            String ownerId,
            String type,
            String title,
            String description,
            String visibility,
            String status,
            Instant startedAt,
            Instant endedAt,
            StatsResponse stats) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StatsResponse(
            double distanceMeters,
            long elapsedSeconds,
            long movingTimeSeconds,
            Long averagePaceSecondsPerKm,
            Long currentPaceSecondsPerKm,
            double elevationGain,
            double elevationLoss,
            Double minAltitude,
            Double maxAltitude,
            Double averageHeartRate,
            Integer maxHeartRate,
            Integer estimatedCalories) {
    }

    /** Pagination par curseur : le client renvoie {@code nextCursor} pour la page suivante. */
    public record ActivityPage(List<ActivityResponse> items, Instant nextCursor) {
    }

    /**
     * La trace historisée.
     *
     * @param polyline la trace simplifiée au format <i>encoded polyline</i> de Google — c'est ce
     *     que la carte dessine, et ce qui pèse quelques kilo-octets au lieu de dizaines
     * @param pointCount ce qu'il en reste après simplification
     * @param rawPointCount ce qu'elle comptait avant ; l'écart dit ce que la simplification a gagné
     * @param pointsPurgedAt renseignée quand les points bruts ont expiré : passé ce moment, la
     *     trace reste affichable mais l'export n'est plus possible
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TrackResponse(
            String polyline,
            int pointCount,
            int rawPointCount,
            Instant frozenAt,
            Instant pointsPurgedAt) {
    }

    /** @param paceSecondsPerKm absente si le tronçon n'a pas duré : il n'y a alors pas d'allure */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SplitResponse(
            int kilometerIndex,
            double distanceMeters,
            long timeSeconds,
            Long paceSecondsPerKm,
            double elevationGain,
            Double averageHeartRate,
            boolean complete) {
    }

    public record SplitsResponse(List<SplitResponse> items) {
    }
}
