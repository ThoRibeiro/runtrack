package com.runtrack.course.event;

import com.runtrack.shared.id.ActivityId;
import java.time.Instant;

/**
 * La portée effective d'une course a changé.
 *
 * <p>Le prompt ne le prévoyait pas. Sans lui, une course passée en privé resterait affichée dans
 * le fil de tous ceux qui la voyaient — la projection de lecture n'ayant aucun moyen d'apprendre
 * que la règle a changé. Une visibilité qu'on peut restreindre sans effet n'en est pas une.
 */
public record ActivityVisibilityChanged(
        ActivityId activityId, String effectiveScope, Instant at, String correlationId) {
}
