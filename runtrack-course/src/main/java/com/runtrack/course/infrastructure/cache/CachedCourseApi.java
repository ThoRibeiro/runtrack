package com.runtrack.course.infrastructure.cache;

import com.runtrack.course.ActivitySummary;
import com.runtrack.course.CourseApi;
import com.runtrack.course.event.ActivityDiscarded;
import com.runtrack.course.event.ActivityFinished;
import com.runtrack.platform.cache.CacheGateway;
import com.runtrack.platform.cache.CacheKey;
import com.runtrack.platform.cache.CacheProperties;
import com.runtrack.shared.access.Viewer;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Le décorateur de cache du contrat {@code course}.
 *
 * <p><b>Seules les courses terminées sont mises en cache.</b> Une course en direct change à
 * chaque lot de points reçu : la cacher ne ferait que servir des chiffres faux, et un TTL
 * assez court pour être juste ne mettrait rien en cache. Les données live vivent sous
 * {@code live:*}, qui est un bus, pas un cache — les doubler ici serait avoir deux sources
 * de vérité pour la même chose.
 *
 * <p>{@code canView} n'est pas caché non plus. Une clé par couple course × lecteur explose
 * en cardinalité, s'invalide mal, et surtout : la décision se recompose gratuitement à
 * partir des faits déjà cachés (portée du compte, abonnements). Cacher une décision
 * d'autorisation, c'est se donner une fenêtre pendant laquelle une porte fermée reste
 * ouverte.
 */
@Component
@Primary
class CachedCourseApi implements CourseApi {

    private static final String FINISHED = "Finished";

    private final CourseApi delegate;
    private final CacheGateway cache;
    private final CacheProperties properties;

    CachedCourseApi(@Qualifier("courseApiAdapter") CourseApi delegate, CacheGateway cache,
            CacheProperties properties) {
        this.delegate = delegate;
        this.cache = cache;
        this.properties = properties;
    }

    @Override
    public Optional<ActivitySummary> summary(ActivityId activityId) {
        Optional<ActivitySummary> cached = cache
                .get(CacheKey.activitySummary(activityId.toString()), CachedSummary.class)
                .map(CachedSummary::toSummary);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<ActivitySummary> loaded = delegate.summary(activityId);
        loaded.filter(CachedCourseApi::isFinished).ifPresent(this::store);
        return loaded;
    }

    @Override
    public Map<ActivityId, ActivitySummary> summaries(Collection<ActivityId> activityIds) {
        var found = new HashMap<ActivityId, ActivitySummary>();
        var missing = new java.util.ArrayList<ActivityId>();

        for (ActivityId id : activityIds) {
            cache.get(CacheKey.activitySummary(id.toString()), CachedSummary.class)
                    .map(CachedSummary::toSummary)
                    .ifPresentOrElse(summary -> found.put(id, summary), () -> missing.add(id));
        }
        if (!missing.isEmpty()) {
            Map<ActivityId, ActivitySummary> loaded = delegate.summaries(missing);
            loaded.values().stream().filter(CachedCourseApi::isFinished).forEach(this::store);
            found.putAll(loaded);
        }
        return Map.copyOf(found);
    }

    @Override
    public Optional<UserId> ownerOf(ActivityId activityId) {
        return summary(activityId).map(ActivitySummary::ownerId);
    }

    @Override
    public boolean canView(Viewer viewer, ActivityId activityId) {
        return delegate.canView(viewer, activityId);
    }

    /** Une course qui se termine, s'édite ou disparaît vide son entrée. */
    @TransactionalEventListener
    void on(ActivityFinished event) {
        cache.evict(CacheKey.activitySummary(event.activityId().toString()));
    }

    @TransactionalEventListener
    void on(ActivityDiscarded event) {
        cache.evict(CacheKey.activitySummary(event.activityId().toString()));
    }

    private void store(ActivitySummary summary) {
        cache.put(CacheKey.activitySummary(summary.id().toString()),
                CachedSummary.from(summary), properties.activitySummaryTtl());
    }

    private static boolean isFinished(ActivitySummary summary) {
        return FINISHED.equals(summary.status());
    }

    record CachedSummary(
            String id, String ownerId, String type, String title, String status,
            String effectiveScope, double distanceMeters, long movingTimeSeconds,
            Instant startedAt, Instant endedAt, String previewPolyline) {

        static CachedSummary from(ActivitySummary summary) {
            return new CachedSummary(summary.id().toString(), summary.ownerId().toString(),
                    summary.type(), summary.title(), summary.status(), summary.effectiveScope(),
                    summary.distanceMeters(), summary.movingTimeSeconds(),
                    summary.startedAt(), summary.endedAt().orElse(null),
                    summary.previewPolyline().orElse(null));
        }

        ActivitySummary toSummary() {
            return new ActivitySummary(ActivityId.of(id), UserId.of(ownerId), type, title, status,
                    effectiveScope, distanceMeters, movingTimeSeconds, startedAt,
                    Optional.ofNullable(endedAt), Optional.ofNullable(previewPolyline));
        }
    }
}
