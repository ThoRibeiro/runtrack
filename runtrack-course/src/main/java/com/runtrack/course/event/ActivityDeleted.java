package com.runtrack.course.event;

import com.runtrack.shared.id.ActivityId;
import java.time.Instant;

/**
 * Une course a été supprimée.
 *
 * <p>Ajouté pour la même raison qu'{@link ActivityVisibilityChanged} : sans lui, la projection du
 * fil garderait indéfiniment une ligne pointant sur une course qui n'existe plus.
 */
public record ActivityDeleted(ActivityId activityId, Instant at, String correlationId) {
}
