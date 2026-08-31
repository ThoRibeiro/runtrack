package com.runtrack.course.usecases.service;

import com.runtrack.course.ActivitySummary;
import com.runtrack.course.CourseApi;
import com.runtrack.course.usecases.port.ActivityRepository;
import com.runtrack.course.usecases.port.ActivityStatsStore;
import com.runtrack.course.usecases.port.ViewerRelationResolver;
import com.runtrack.course.usecases.model.activity.Activity;
import com.runtrack.course.usecases.model.stats.StatsAccumulator;
import com.runtrack.shared.access.Viewer;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** L'implémentation du contrat public du module. Traduit, ne décide pas. */
@Service("courseApiAdapter")
class CourseApiAdapter implements CourseApi {

    private final ActivityRepository activities;
    private final ActivityStatsStore stats;
    private final ViewerRelationResolver relations;
    private final ActivityQueries queries;

    CourseApiAdapter(ActivityRepository activities, ActivityStatsStore stats,
            ViewerRelationResolver relations, ActivityQueries queries) {
        this.activities = activities;
        this.stats = stats;
        this.relations = relations;
        this.queries = queries;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ActivitySummary> summary(ActivityId activityId) {
        return activities.findById(activityId).map(this::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<ActivityId, ActivitySummary> summaries(Collection<ActivityId> activityIds) {
        if (activityIds.isEmpty()) {
            return Map.of();
        }
        return activities.findAllById(activityIds).stream()
                .map(this::toSummary)
                .collect(Collectors.toMap(ActivitySummary::id, Function.identity()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserId> ownerOf(ActivityId activityId) {
        return activities.findById(activityId).map(Activity::ownerId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canView(Viewer viewer, ActivityId activityId) {
        return queries.canView(viewer, activityId);
    }

    private ActivitySummary toSummary(Activity activity) {
        StatsAccumulator accumulator = stats.find(activity.id()).orElseGet(StatsAccumulator::empty);
        return new ActivitySummary(
                activity.id(),
                activity.ownerId(),
                activity.type().name(),
                activity.title(),
                activity.status().getClass().getSimpleName(),
                activity.audienceWith(relations.accountScopeOf(activity.ownerId())).effectiveScope().name(),
                accumulator.distance().meters(),
                accumulator.movingTime().toSeconds(),
                activity.startedAt(),
                activity.status().isTerminal() ? Optional.of(activity.status().since()) : Optional.empty());
    }
}
