package com.runtrack.course.internal.infra.jpa;

import com.runtrack.course.internal.application.port.IdempotencyStore;
import com.runtrack.course.internal.infra.jpa.entity.IdempotencyKeyEntity;
import com.runtrack.shared.id.ActivityId;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaIdempotencyStore implements IdempotencyStore {

    private final SpringDataIdempotencyRepository entities;
    private final Clock clock;

    JpaIdempotencyStore(SpringDataIdempotencyRepository entities, Clock clock) {
        this.entities = entities;
        this.clock = clock;
    }

    @Override
    public Optional<StoredResponse> find(ActivityId activityId, String key) {
        return entities.findById(IdempotencyKeyEntity.idOf(activityId.value(), key))
                .map(entity -> new StoredResponse(entity.getRequestDigest(), entity.getResponseBody()));
    }

    @Override
    public void store(ActivityId activityId, String key, String requestDigest, String responseBody) {
        entities.save(new IdempotencyKeyEntity(
                activityId.value(), key, requestDigest, responseBody, clock.instant()));
    }
}
