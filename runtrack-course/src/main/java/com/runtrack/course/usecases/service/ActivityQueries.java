package com.runtrack.course.usecases.service;

import com.runtrack.course.usecases.port.ActivityRepository;
import com.runtrack.course.usecases.port.ActivityStatsStore;
import com.runtrack.course.usecases.port.ViewerRelationResolver;
import com.runtrack.course.usecases.model.access.AccessDecision;
import com.runtrack.course.usecases.model.access.ActivityAccessPolicy;
import com.runtrack.course.usecases.model.activity.Activity;
import com.runtrack.course.usecases.model.stats.ActivityStats;
import com.runtrack.course.usecases.model.stats.StatsAccumulator;
import com.runtrack.course.usecases.model.stats.StatsCalculator;
import com.runtrack.shared.access.Viewer;
import com.runtrack.shared.error.NotFoundException;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import com.runtrack.user.UserApi;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Les lectures de courses, toutes passées au tamis de la règle d'accès.
 *
 * <p>Aucun chemin de lecture ne contourne {@link ActivityAccessPolicy} : c'est ce qui rend
 * vrai le « une règle, un seul endroit ».
 */
@Service
public class ActivityQueries {

    private final ActivityRepository activities;
    private final ActivityStatsStore stats;
    private final ViewerRelationResolver relations;
    private final UserApi users;
    private final Clock clock;

    public ActivityQueries(ActivityRepository activities, ActivityStatsStore stats,
            ViewerRelationResolver relations, UserApi users, Clock clock) {
        this.activities = activities;
        this.stats = stats;
        this.relations = relations;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AccessDecision decide(Viewer viewer, Activity activity) {
        return ActivityAccessPolicy.canView(
                viewer,
                relations.audienceOf(activity.id(), activity.ownerId(), activity.scope()),
                relations.relationOf(viewer, activity.ownerId()));
    }

    @Transactional(readOnly = true)
    public boolean canView(Viewer viewer, ActivityId id) {
        return activities.findById(id).map(activity -> decide(viewer, activity).isGranted()).orElse(false);
    }

    /**
     * Une course visible par ce lecteur.
     *
     * <p>Un refus rend « introuvable », jamais « interdit » : distinguer les deux
     * confirmerait l'existence de la course à qui n'a pas le droit de la voir.
     */
    @Transactional(readOnly = true)
    public Activity require(Viewer viewer, ActivityId id) {
        Activity activity = activities.findById(id).orElseThrow(ActivityQueries::notFound);
        if (!decide(viewer, activity).isGranted()) {
            throw notFound();
        }
        return activity;
    }

    @Transactional(readOnly = true)
    public ActivityStats statsOf(Activity activity) {
        StatsAccumulator accumulator = stats.find(activity.id()).orElseGet(StatsAccumulator::empty);
        return StatsCalculator.summarize(
                accumulator,
                activity.elapsedAt(clock.instant()),
                activity.type(),
                users.massOf(activity.ownerId())
                        .map(mass -> new com.runtrack.course.usecases.model.stats.RunnerPhysiology(
                                mass.kilograms())));
    }

    /** Les courses d'un utilisateur, filtrées par ce que le lecteur a le droit de voir. */
    @Transactional(readOnly = true)
    public List<Activity> ofOwner(Viewer viewer, UserId ownerId, Optional<Instant> before, int limit) {
        return activities.findByOwner(ownerId, before, limit).stream()
                .filter(activity -> decide(viewer, activity).isGranted())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Activity> liveOf(Viewer viewer, java.util.Collection<UserId> ownerIds) {
        return activities.findLiveOf(ownerIds).stream()
                .filter(activity -> decide(viewer, activity).isGranted())
                .toList();
    }

    private static NotFoundException notFound() {
        return new NotFoundException("ACTIVITY_NOT_FOUND", "Course introuvable");
    }
}
