package com.runtrack.course.internal.infra.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * Les charges utiles du direct.
 *
 * <p>Elles voyagent d'abord dans le Stream Dragonfly, puis telles quelles vers le client SSE :
 * c'est le même JSON, écrit une fois. Les statistiques réutilisent
 * {@link ActivityDtos.StatsResponse}, pour que {@code GET /activities/{id}} et l'événement
 * {@code stats} ne présentent pas deux formes différentes de la même chose.
 */
public final class LiveDtos {

    private LiveDtos() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PositionEvent(
            int sequenceNumber,
            double latitude,
            double longitude,
            double elevation,
            Instant recordedAt,
            Integer heartRate) {
    }

    public record StatusEvent(String status, Instant since) {
    }
}
