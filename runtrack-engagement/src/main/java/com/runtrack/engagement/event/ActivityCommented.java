package com.runtrack.engagement.event;

import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import java.time.Instant;

/** Un commentaire de premier niveau sur une course. */
public record ActivityCommented(
        ActivityId activityId, UserId ownerId, UserId authorId, String commentId,
        Instant at, String correlationId) {
}
