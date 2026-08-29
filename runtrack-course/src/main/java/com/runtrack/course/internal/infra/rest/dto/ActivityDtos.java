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
}
