package com.runtrack.engagement.infrastructure.cache;

import com.runtrack.engagement.usecases.model.interaction.ActivityCounters;
import com.runtrack.engagement.usecases.port.ActivityCountersRepository;
import com.runtrack.platform.cache.CacheGateway;
import com.runtrack.platform.cache.CacheKey;
import com.runtrack.platform.cache.CacheProperties;
import com.runtrack.shared.id.ActivityId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Le décorateur de cache des compteurs.
 *
 * <p>Une minute de TTL, et c'est volontairement court : un compteur est ce que le §6 appelle une
 * donnée chaude et peu grave. Le lire une seconde en retard n'induit personne en erreur, alors que
 * le recompter à chaque affichage de course est deux agrégations sur les deux tables les plus
 * écrites du module.
 */
@Component
@Primary
class CachedActivityCountersRepository implements ActivityCountersRepository {

    private final ActivityCountersRepository delegate;
    private final CacheGateway cache;
    private final CacheProperties properties;

    CachedActivityCountersRepository(
            @Qualifier("jdbcActivityCountersRepository") ActivityCountersRepository delegate,
            CacheGateway cache, CacheProperties properties) {

        this.delegate = delegate;
        this.cache = cache;
        this.properties = properties;
    }

    @Override
    public ActivityCounters countersOf(ActivityId activityId) {
        ActivityCounters counters = cache.getOrLoad(
                CacheKey.activityCounters(activityId.toString()), ActivityCounters.class,
                properties.countersTtl(), () -> delegate.countersOf(activityId));
        return counters == null ? ActivityCounters.NONE : counters;
    }
}
