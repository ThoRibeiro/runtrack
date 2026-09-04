package com.runtrack.course;

import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import java.time.Instant;
import java.util.Optional;

/**
 * Ce qu'un autre module a le droit de savoir d'une course : de quoi afficher une ligne de
 * fil ou une notification. Ni trace GPS brute, ni statistiques détaillées.
 *
 * <p>{@code previewPolyline} fait exception, et c'en est une assumée : une ligne de fil montre
 * le parcours, et l'alternative serait que {@code feed} aille lire {@code activity_tracks} —
 * la jointure inter-modules que le §10 interdit. C'est une vignette, pas la trace : quelques
 * dizaines de points, de quoi dessiner une forme et rien de plus.
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
        Optional<Instant> endedAt,
        Optional<String> previewPolyline) {

    public ActivitySummary {
        if (id == null || ownerId == null || type == null || title == null || status == null
                || effectiveScope == null || startedAt == null || endedAt == null
                || previewPolyline == null) {
            throw new IllegalArgumentException("ActivitySummary incomplet");
        }
    }
}
