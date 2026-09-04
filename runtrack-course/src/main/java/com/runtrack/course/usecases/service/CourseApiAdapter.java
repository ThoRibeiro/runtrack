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
import java.util.List;
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
    private final ActivityArchival archival;

    CourseApiAdapter(ActivityRepository activities, ActivityStatsStore stats,
            ViewerRelationResolver relations, ActivityQueries queries, ActivityArchival archival) {
        this.activities = activities;
        this.stats = stats;
        this.relations = relations;
        this.queries = queries;
        this.archival = archival;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ActivitySummary> summary(ActivityId activityId) {
        // Par le service et non par l'archive : lui sait aussi tirer une vignette d'une
        // course qui court encore, qui n'a rien d'historisé.
        return activities.findById(activityId)
                .map(activity -> toSummary(activity,
                        Optional.ofNullable(archival.previewsOf(List.of(activityId)).get(activityId))));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<ActivityId, ActivitySummary> summaries(Collection<ActivityId> activityIds) {
        if (activityIds.isEmpty()) {
            return Map.of();
        }
        // Les vignettes en une requête : une par course ferait le N+1 du §10 sur un fil.
        Map<ActivityId, String> previews = archival.previewsOf(activityIds);

        return activities.findAllById(activityIds).stream()
                .map(activity -> toSummary(activity,
                        Optional.ofNullable(previews.get(activity.id()))))
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

    private ActivitySummary toSummary(Activity activity, Optional<String> previewPolyline) {
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
                activity.status().isTerminal() ? Optional.of(activity.status().since()) : Optional.empty(),
                // Vide tant que la course n'est pas historisée : la vignette est calculée au
                // gel de fin de course.
                previewPolyline);
    }
}
