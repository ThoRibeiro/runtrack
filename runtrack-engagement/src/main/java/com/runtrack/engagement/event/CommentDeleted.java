package com.runtrack.engagement.event;

import com.runtrack.shared.id.ActivityId;
import java.time.Instant;

/** Un commentaire supprimé — le compteur du fil doit redescendre. */
public record CommentDeleted(ActivityId activityId, String commentId, Instant at, String correlationId) {
}
