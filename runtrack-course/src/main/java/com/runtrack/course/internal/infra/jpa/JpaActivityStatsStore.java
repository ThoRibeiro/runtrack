package com.runtrack.course.internal.infra.jpa;

import com.runtrack.course.internal.application.port.ActivityStatsStore;
import com.runtrack.course.internal.domain.stats.StatsAccumulator;
import com.runtrack.course.internal.infra.jpa.entity.ActivityStatsEntity;
import com.runtrack.shared.id.ActivityId;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/**
 * Persiste l'accumulateur en JSON, doublé de colonnes dérivées.
 *
 * <p>Le JSON seul suffirait à la reprise de l'incrémental, mais il faut pouvoir trier et
 * filtrer sur la distance sans désérialiser une ligne par course.
 */
@Repository
class JpaActivityStatsStore implements ActivityStatsStore {

    private final SpringDataActivityStatsRepository entities;
    private final ObjectMapper json;

    JpaActivityStatsStore(SpringDataActivityStatsRepository entities, ObjectMapper json) {
        this.entities = entities;
        this.json = json;
    }

    @Override
    public Optional<StatsAccumulator> find(ActivityId activityId) {
        return entities.findById(activityId.value())
                .map(entity -> json.readValue(entity.getAccumulatorState(), StoredAccumulator.class))
                .map(StoredAccumulator::toDomain);
    }

    @Override
    public void save(ActivityId activityId, StatsAccumulator accumulator) {
        var incoming = new ActivityStatsEntity(
                activityId.value(),
                json.writeValueAsString(StoredAccumulator.from(accumulator)),
                accumulator.lastAppliedSequence(),
                accumulator.distance().meters(),
                accumulator.movingTime().toSeconds(),
                accumulator.elevation().gain(),
                accumulator.elevation().loss());

        entities.save(entities.findById(activityId.value())
                .map(existing -> {
                    existing.refreshFrom(incoming);
                    return existing;
                })
                .orElse(incoming));
    }

    @Override
    public void delete(ActivityId activityId) {
        entities.deleteById(activityId.value());
    }
}
