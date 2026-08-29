package com.runtrack.course.event;

import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import java.time.Instant;

/** Statistiques figées, course historisée. Déclenche notification, feed et purge du live. */
public record ActivityFinished(
        ActivityId activityId,
        UserId ownerId,
        String effectiveScope,
        double distanceMeters,
        long movingTimeSeconds,
        Instant at,
        String correlationId) {
}
