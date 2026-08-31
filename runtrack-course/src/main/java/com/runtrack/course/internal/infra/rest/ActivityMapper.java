package com.runtrack.course.internal.infra.rest;

import com.runtrack.course.internal.domain.activity.Activity;
import com.runtrack.course.internal.domain.stats.ActivityStats;
import com.runtrack.course.internal.infra.rest.dto.ActivityDtos;
import com.runtrack.shared.measure.Elevation;
import com.runtrack.shared.measure.Pace;

/** Agrégat et statistiques vers DTO, à la main. */
final class ActivityMapper {

    private ActivityMapper() {
    }

    static ActivityDtos.ActivityResponse toResponse(Activity activity, ActivityStats stats) {
        return new ActivityDtos.ActivityResponse(
                activity.id().toString(),
                activity.ownerId().toString(),
                activity.type().name(),
                activity.title(),
                activity.description().orElse(null),
                activity.scope().name(),
                activity.status().getClass().getSimpleName(),
                activity.startedAt(),
                activity.status().isTerminal() ? activity.status().since() : null,
                toStats(stats));
    }

    static ActivityDtos.StatsResponse toStats(ActivityStats stats) {
        return new ActivityDtos.StatsResponse(
                stats.distance().meters(),
                stats.elapsed().toSeconds(),
                stats.movingTime().toSeconds(),
                stats.averagePace().map(ActivityMapper::secondsPerKilometer).orElse(null),
                stats.currentPace().map(ActivityMapper::secondsPerKilometer).orElse(null),
                stats.elevationGain(),
                stats.elevationLoss(),
                stats.minAltitude().map(Elevation::meters).orElse(null),
                stats.maxAltitude().map(Elevation::meters).orElse(null),
                stats.averageHeartRate().isPresent() ? stats.averageHeartRate().getAsDouble() : null,
                stats.maxHeartRate().isPresent() ? stats.maxHeartRate().getAsInt() : null,
                stats.estimatedCalories().isPresent() ? stats.estimatedCalories().getAsInt() : null);
    }

    private static Long secondsPerKilometer(Pace pace) {
        return pace.perKilometer().toSeconds();
    }
}
