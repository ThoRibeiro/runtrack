package com.runtrack.course.infrastructure.endpoint;

import com.runtrack.course.usecases.model.activity.Activity;
import com.runtrack.course.usecases.model.stats.ActivityStats;
import com.runtrack.course.infrastructure.dto.ActivityDtos;
import com.runtrack.shared.measure.Elevation;
import com.runtrack.shared.measure.Pace;
import com.runtrack.user.UserSummary;

/**
 * Agrégat et statistiques vers DTO, à la main.
 *
 * <p>Public parce que le direct s'en sert aussi : l'événement {@code stats} d'une course en
 * cours doit présenter exactement la même forme que sa lecture REST, sans quoi le client
 * écrirait deux fois le même affichage.
 */
public final class ActivityMapper {

    private ActivityMapper() {
    }

    public static ActivityDtos.ActivityResponse toResponse(Activity activity, ActivityStats stats) {
        return toResponse(activity, stats, null, null);
    }

    /**
     * @param author absent quand l'appelant n'a pas de profil à donner — le direct, par exemple,
     *     qui rediffuse des chiffres sans jamais changer d'auteur au fil de la course
     */
    public static ActivityDtos.ActivityResponse toResponse(Activity activity, ActivityStats stats,
            String previewPolyline, UserSummary author) {

        return new ActivityDtos.ActivityResponse(
                activity.id().toString(),
                activity.ownerId().toString(),
                toAuthor(author),
                activity.type().name(),
                activity.title(),
                activity.description().orElse(null),
                activity.scope().name(),
                activity.status().getClass().getSimpleName(),
                activity.startedAt(),
                activity.status().isTerminal() ? activity.status().since() : null,
                toStats(stats),
                previewPolyline);
    }

    private static ActivityDtos.AuthorDto toAuthor(UserSummary author) {
        if (author == null) {
            return null;
        }
        return new ActivityDtos.AuthorDto(author.id().toString(), author.handle(),
                author.displayName(), author.avatarUrl().orElse(null));
    }

    public static ActivityDtos.StatsResponse toStats(ActivityStats stats) {
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

    static ActivityDtos.TrackResponse toTrack(
            com.runtrack.course.usecases.port.ActivityArchive.ArchivedTrack track) {

        return new ActivityDtos.TrackResponse(
                track.polyline(),
                track.pointCount(),
                track.rawPointCount(),
                track.frozenAt(),
                track.pointsPurgedAt().orElse(null));
    }

    static ActivityDtos.SplitResponse toSplit(
            com.runtrack.course.usecases.model.stats.Split split) {

        return new ActivityDtos.SplitResponse(
                split.kilometerIndex(),
                split.distance().meters(),
                split.time().toSeconds(),
                split.pace().map(ActivityMapper::secondsPerKilometer).orElse(null),
                split.elevationGain(),
                split.averageHeartRate().isPresent() ? split.averageHeartRate().getAsDouble() : null,
                split.isComplete());
    }
}
