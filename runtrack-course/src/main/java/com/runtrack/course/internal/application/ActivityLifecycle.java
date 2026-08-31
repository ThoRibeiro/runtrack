package com.runtrack.course.internal.application;

import com.runtrack.course.event.ActivityDeleted;
import com.runtrack.course.event.ActivityDiscarded;
import com.runtrack.course.event.ActivityFinished;
import com.runtrack.course.event.ActivityPaused;
import com.runtrack.course.event.ActivityResumed;
import com.runtrack.course.event.ActivityStarted;
import com.runtrack.course.event.ActivityVisibilityChanged;
import com.runtrack.course.internal.application.port.ActivityRepository;
import com.runtrack.course.internal.application.port.ActivityStatsStore;
import com.runtrack.course.internal.application.port.LiveActivityPublisher;
import com.runtrack.course.internal.application.port.ViewerRelationResolver;
import com.runtrack.course.internal.domain.activity.Activity;
import com.runtrack.course.internal.domain.activity.ActivityType;
import com.runtrack.course.internal.domain.live.LiveEvent;
import com.runtrack.course.internal.domain.stats.StatsAccumulator;
import com.runtrack.course.internal.domain.track.DeviceClockSkew;
import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.context.CallContext;
import com.runtrack.shared.error.ForbiddenException;
import com.runtrack.shared.error.NotFoundException;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.random.RandomGenerator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Le cycle de vie d'une course : démarrer, mettre en pause, reprendre, terminer, abandonner.
 *
 * <p>Chaque événement publié porte le {@code correlationId} de la requête. Le contexte
 * d'appel étant un {@code ScopedValue}, il ne franchit pas la frontière asynchrone d'un
 * listener : le transporter dans l'événement est la seule façon de garder des journaux
 * corrélés au-delà du chemin HTTP.
 */
@Service
public class ActivityLifecycle {

    private final ActivityRepository activities;
    private final ActivityStatsStore stats;
    private final ViewerRelationResolver relations;
    private final ApplicationEventPublisher events;
    private final ActivityArchival archival;
    private final LiveActivityPublisher live;
    private final Clock clock;
    private final RandomGenerator random;

    public ActivityLifecycle(ActivityRepository activities, ActivityStatsStore stats,
            ViewerRelationResolver relations, ApplicationEventPublisher events,
            ActivityArchival archival, LiveActivityPublisher live, Clock clock,
            RandomGenerator random) {
        this.activities = activities;
        this.stats = stats;
        this.relations = relations;
        this.events = events;
        this.archival = archival;
        this.live = live;
        this.clock = clock;
        this.random = random;
    }

    @Transactional
    public Activity start(UserId ownerId, ActivityType type, String title, String description,
            AudienceScope scope, Instant deviceTime) {

        Instant now = clock.instant();
        DeviceClockSkew skew = deviceTime == null
                ? DeviceClockSkew.NONE
                : DeviceClockSkew.observe(deviceTime, now);

        Activity activity = activities.save(Activity.start(
                ActivityId.generate(clock, random), ownerId, type, title, description, scope, now, skew));
        stats.save(activity.id(), StatsAccumulator.empty());

        events.publishEvent(new ActivityStarted(activity.id(), ownerId, type.name(),
                effectiveScopeOf(activity).name(), now, correlationId()));
        return activity;
    }

    @Transactional
    public void pause(UserId ownerId, ActivityId id) {
        Activity activity = requireOwned(ownerId, id);
        Instant now = clock.instant();
        activity.pause(now);
        activities.save(activity);
        events.publishEvent(new ActivityPaused(id, now, correlationId()));
        broadcastStatus(activity, now);
    }

    @Transactional
    public void resume(UserId ownerId, ActivityId id) {
        Activity activity = requireOwned(ownerId, id);
        Instant now = clock.instant();
        activity.resume(now);
        activities.save(activity);
        events.publishEvent(new ActivityResumed(id, now, correlationId()));
        broadcastStatus(activity, now);
    }

    @Transactional
    public void finish(UserId ownerId, ActivityId id) {
        Activity activity = requireOwned(ownerId, id);
        Instant now = clock.instant();
        activity.finish(now);
        activities.save(activity);
        // Avant l'événement : les écouteurs — le fil, les notifications — parlent d'une course
        // terminée, et celle-ci doit déjà porter ses splits quand ils la relisent.
        archival.freeze(activity);

        StatsAccumulator accumulator = stats.find(id).orElseGet(StatsAccumulator::empty);
        events.publishEvent(new ActivityFinished(id, ownerId, effectiveScopeOf(activity).name(),
                accumulator.distance().meters(), accumulator.movingTime().toSeconds(), now, correlationId()));
        endBroadcast(activity, now);
    }

    @Transactional
    public void discard(UserId ownerId, ActivityId id) {
        Activity activity = requireOwned(ownerId, id);
        Instant now = clock.instant();
        activity.discard(now);
        activities.save(activity);
        events.publishEvent(new ActivityDiscarded(id, ownerId, now, correlationId()));
        endBroadcast(activity, now);
    }

    @Transactional
    public Activity rename(UserId ownerId, ActivityId id, String title, String description) {
        Activity activity = requireOwned(ownerId, id);
        activity.rename(title, description);
        return activities.save(activity);
    }

    @Transactional
    public Activity changeScope(UserId ownerId, ActivityId id, AudienceScope scope) {
        Activity activity = requireOwned(ownerId, id);
        activity.changeScope(scope);
        Activity saved = activities.save(activity);
        // Le fil doit l'apprendre : une course repassée en privé n'a plus rien à y faire, et la
        // projection n'a aucun autre moyen de le savoir.
        events.publishEvent(new ActivityVisibilityChanged(
                id, effectiveScopeOf(saved).name(), clock.instant(), correlationId()));
        return saved;
    }

    @Transactional
    public void delete(UserId ownerId, ActivityId id) {
        requireOwned(ownerId, id);
        stats.delete(id);
        archival.purge(id);
        activities.delete(id);
        events.publishEvent(new ActivityDeleted(id, clock.instant(), correlationId()));
    }

    private void broadcastStatus(Activity activity, Instant now) {
        live.publish(activity.id(),
                List.of(new LiveEvent.Status(activity.status().getClass().getSimpleName(), now)));
    }

    /**
     * Annonce la fin, puis ferme le direct — dans cet ordre.
     *
     * <p>Fermer d'abord priverait les spectateurs connectés de la seule information qui les
     * intéresse encore : que la course est terminée.
     */
    private void endBroadcast(Activity activity, Instant now) {
        broadcastStatus(activity, now);
        live.closeStream(activity.id());
    }

    /**
     * Charge la course en exigeant qu'elle appartienne à l'appelant.
     *
     * <p>Répond « introuvable » et non « interdit » quand elle est à quelqu'un d'autre :
     * un 403 confirmerait l'existence de la course à qui n'a pas le droit de la voir.
     */
    private Activity requireOwned(UserId ownerId, ActivityId id) {
        Activity activity = activities.findById(id).orElseThrow(ActivityLifecycle::notFound);
        if (!activity.ownerId().equals(ownerId)) {
            throw notFound();
        }
        return activity;
    }

    private AudienceScope effectiveScopeOf(Activity activity) {
        return activity.audienceWith(relations.accountScopeOf(activity.ownerId())).effectiveScope();
    }

    private static NotFoundException notFound() {
        return new NotFoundException("ACTIVITY_NOT_FOUND", "Course introuvable");
    }

    static ForbiddenException forbidden() {
        return new ForbiddenException("ACTIVITY_NOT_VISIBLE", "Cette course ne vous est pas accessible");
    }

    private static String correlationId() {
        return CallContext.current().map(CallContext::correlationId).orElse("unknown");
    }
}
