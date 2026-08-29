package com.runtrack.course.internal.application.port;

import com.runtrack.course.internal.domain.stats.StatsAccumulator;
import com.runtrack.shared.id.ActivityId;
import java.util.Optional;

/**
 * L'accumulateur de statistiques, persisté à part de l'agrégat.
 *
 * <p>Séparé parce qu'il change à chaque lot de points reçu, alors que la course elle-même
 * ne change qu'aux transitions. Les mêler ferait porter le verrou optimiste de l'agrégat
 * sur chaque écriture de statistiques.
 */
public interface ActivityStatsStore {

    Optional<StatsAccumulator> find(ActivityId activityId);

    void save(ActivityId activityId, StatsAccumulator accumulator);

    void delete(ActivityId activityId);
}
