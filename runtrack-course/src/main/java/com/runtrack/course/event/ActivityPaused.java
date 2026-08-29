package com.runtrack.course.event;

import com.runtrack.shared.id.ActivityId;
import java.time.Instant;

public record ActivityPaused(ActivityId activityId, Instant at, String correlationId) {
}
