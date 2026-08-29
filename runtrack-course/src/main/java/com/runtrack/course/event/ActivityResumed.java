package com.runtrack.course.event;

import com.runtrack.shared.id.ActivityId;
import java.time.Instant;

public record ActivityResumed(ActivityId activityId, Instant at, String correlationId) {
}
