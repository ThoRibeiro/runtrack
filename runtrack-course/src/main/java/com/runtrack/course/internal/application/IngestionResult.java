package com.runtrack.course.internal.application;

import com.runtrack.course.internal.domain.stats.ActivityStats;
import com.runtrack.course.internal.domain.track.PointRejection;
import java.util.List;

/**
 * Ce que l'ingestion rend au client.
 *
 * <p>{@code lastAcceptedSequence} lui dit jusqu'où purger son tampon ; les rejets lui
 * disent ce qui a été écarté et pourquoi, afin qu'il distingue un doublon attendu d'un
 * capteur qui déraille.
 */
public record IngestionResult(
        ActivityStats stats,
        int lastAcceptedSequence,
        int acceptedCount,
        List<Rejected> rejected) {

    public record Rejected(int sequenceNumber, PointRejection reason) {
    }
}
