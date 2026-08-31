package com.runtrack.course.infrastructure.repository;

import com.runtrack.course.usecases.port.ActivityRepository;
import com.runtrack.course.usecases.model.activity.Activity;
import com.runtrack.course.infrastructure.repository.entity.ActivityEntity;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

@Repository
class JpaActivityRepository implements ActivityRepository {

    private static final String LIVE = "Live";

    private final SpringDataActivityRepository entities;

    JpaActivityRepository(SpringDataActivityRepository entities) {
        this.entities = entities;
    }

    @Override
    public Optional<Activity> findById(ActivityId id) {
        return entities.findById(id.value()).map(ActivityEntityMapper::toDomain);
    }

    @Override
    public List<Activity> findAllById(Collection<ActivityId> ids) {
        return entities.findAllByIdIn(ids.stream().map(ActivityId::value).toList()).stream()
                .map(ActivityEntityMapper::toDomain)
                .toList();
    }

    @Override
    public List<Activity> findByOwner(UserId ownerId, Optional<Instant> before, int limit) {
        List<ActivityEntity> found = before
                .map(cursor -> entities.findAllByOwnerIdAndStartedAtLessThanOrderByStartedAtDesc(
                        ownerId.value(), cursor, Limit.of(limit)))
                .orElseGet(() -> entities.findAllByOwnerIdOrderByStartedAtDesc(
                        ownerId.value(), Limit.of(limit)));
        return found.stream().map(ActivityEntityMapper::toDomain).toList();
    }

    @Override
    public List<Activity> findLiveOf(Collection<UserId> ownerIds) {
        if (ownerIds.isEmpty()) {
            return List.of();
        }
        return entities
                .findAllByOwnerIdInAndStatus(ownerIds.stream().map(UserId::value).toList(), LIVE).stream()
                .map(ActivityEntityMapper::toDomain)
                .toList();
    }

    /** Met à jour la ligne existante pour laisser le verrou optimiste faire son travail. */
    @Override
    public Activity save(Activity activity) {
        ActivityEntity incoming = ActivityEntityMapper.toEntity(activity);
        entities.save(entities.findById(activity.id().value())
                .map(existing -> {
                    existing.refreshFrom(incoming);
                    return existing;
                })
                .orElse(incoming));
        return activity;
    }

    @Override
    public void delete(ActivityId id) {
        entities.deleteById(id.value());
    }
}
