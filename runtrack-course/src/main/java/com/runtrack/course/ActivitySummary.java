package com.runtrack.course;

import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import java.time.Instant;
import java.util.Optional;

/**
 * Ce qu'un autre module a le droit de savoir d'une course : de quoi afficher une ligne de
 * fil ou une notification. Ni trace GPS, ni statistiques détaillées.
 */
public record ActivitySummary(
        ActivityId id,
        UserId ownerId,
        String type,
        String title,
        String status,
        String effectiveScope,
        double distanceMeters,
        long movingTimeSeconds,
        Instant startedAt,
        Optional<Instant> endedAt) {

    public ActivitySummary {
        if (id == null || ownerId == null || type == null || title == null
                || status == null || effectiveScope == null || startedAt == null || endedAt == null) {
            throw new IllegalArgumentException("ActivitySummary incomplet");
        }
    }
}
