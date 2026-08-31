package com.runtrack.feed.internal.domain.entry;

import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import java.time.Instant;
import java.util.Optional;

/**
 * Une course telle que le fil l'affiche.
 *
 * <p>Une projection, pas un agrégat : elle ne décide de rien, elle est le reflet de ce que
 * {@code course} et {@code engagement} ont publié. Elle ne porte que ce qu'une ligne de fil montre
 * — ni trace GPS, ni statistiques détaillées, ni identité de ceux qui ont aimé.
 */
public record FeedEntry(
        ActivityId activityId,
        UserId ownerId,
        String type,
        String title,
        String status,
        AudienceScope effectiveScope,
        double distanceMeters,
        long movingTimeSeconds,
        Instant startedAt,
        Optional<Instant> endedAt,
        long likeCount,
        long commentCount) {

    public FeedEntry {
        if (activityId == null || ownerId == null || type == null || title == null
                || status == null || effectiveScope == null || startedAt == null || endedAt == null) {
            throw new IllegalArgumentException("Entrée de fil incomplète");
        }
    }

    /**
     * Une course effectivement privée n'apparaît dans aucun fil, pas même après coup.
     *
     * <p>La ligne n'est pas supprimée pour autant : le propriétaire peut rouvrir sa course, et la
     * reconstruire demanderait de rejouer un historique qu'on n'a plus.
     */
    public boolean isVisibleToFollowers() {
        return effectiveScope != AudienceScope.PRIVATE;
    }
}
