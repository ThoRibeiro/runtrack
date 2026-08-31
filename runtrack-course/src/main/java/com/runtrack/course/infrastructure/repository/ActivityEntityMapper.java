package com.runtrack.course.infrastructure.repository;

import com.runtrack.course.usecases.model.activity.Activity;
import com.runtrack.course.usecases.model.activity.ActivityStatus;
import com.runtrack.course.usecases.model.activity.ActivityType;
import com.runtrack.course.usecases.model.track.DeviceClockSkew;
import com.runtrack.course.infrastructure.repository.entity.ActivityEntity;
import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import java.time.Duration;
import java.time.Instant;

/** Traduction entre l'agrégat et sa ligne, à la main. */
final class ActivityEntityMapper {

    private ActivityEntityMapper() {
    }

    static ActivityEntity toEntity(Activity activity) {
        return new ActivityEntity(
                activity.id().value(),
                activity.ownerId().value(),
                activity.type().name(),
                activity.title(),
                activity.description().orElse(null),
                activity.scope().name(),
                activity.status().getClass().getSimpleName(),
                activity.status().since(),
                activity.startedAt(),
                activity.clockSkew().offset().toNanos());
    }

    static Activity toDomain(ActivityEntity entity) {
        return Activity.rehydrate(
                new ActivityId(entity.getId()),
                new UserId(entity.getOwnerId()),
                ActivityType.valueOf(entity.getType()),
                entity.getTitle(),
                entity.getDescription(),
                AudienceScope.valueOf(entity.getActivityScope()),
                toStatus(entity.getStatus(), entity.getStatusSince()),
                entity.getStartedAt(),
                new DeviceClockSkew(Duration.ofNanos(entity.getClockSkewNanos())));
    }

    /**
     * Le nom simple du record scellé sert de discriminant. Une valeur inconnue fait échouer
     * la lecture plutôt que de rendre une course dans un état inventé.
     */
    private static ActivityStatus toStatus(String name, Instant since) {
        return switch (name) {
            case "Live" -> new ActivityStatus.Live(since);
            case "Paused" -> new ActivityStatus.Paused(since);
            case "Finished" -> new ActivityStatus.Finished(since);
            case "Discarded" -> new ActivityStatus.Discarded(since);
            default -> throw new IllegalStateException("État de course inconnu en base : " + name);
        };
    }
}
