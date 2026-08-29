package com.runtrack.course.event;

import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import java.time.Instant;

public record ActivityDiscarded(ActivityId activityId, UserId ownerId, Instant at, String correlationId) {
}
