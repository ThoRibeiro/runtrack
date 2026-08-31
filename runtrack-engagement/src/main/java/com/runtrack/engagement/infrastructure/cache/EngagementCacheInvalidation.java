package com.runtrack.engagement.infrastructure.cache;

import com.runtrack.engagement.event.ActivityCommented;
import com.runtrack.engagement.event.ActivityLiked;
import com.runtrack.engagement.event.ActivityUnliked;
import com.runtrack.engagement.event.CommentDeleted;
import com.runtrack.engagement.event.CommentReplied;
import com.runtrack.platform.cache.CacheKey;
import com.runtrack.platform.cache.CacheGateway;
import com.runtrack.shared.id.ActivityId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Purge les compteurs dès qu'un geste les fait bouger.
 *
 * <p>{@code @TransactionalEventListener} et non {@code @EventListener} : l'invalidation doit avoir
 * lieu <b>après le commit</b>. Purger avant laisserait une lecture concurrente recharger l'ancienne
 * valeur depuis la base et la remettre en cache — un cache remis à jour avec ce qu'on venait d'en
 * chasser.
 *
 * <p>Un écouteur par événement plutôt qu'un seul sur une interface commune : ces cinq
 * enregistrements n'ont pas d'ancêtre commun, et leur en inventer un ferait exister un type dont le
 * seul rôle serait de servir ce cache.
 */
@Component
class EngagementCacheInvalidation {

    private final CacheGateway cache;

    EngagementCacheInvalidation(CacheGateway cache) {
        this.cache = cache;
    }

    @TransactionalEventListener
    void on(ActivityLiked event) {
        evict(event.activityId());
    }

    @TransactionalEventListener
    void on(ActivityUnliked event) {
        evict(event.activityId());
    }

    @TransactionalEventListener
    void on(ActivityCommented event) {
        evict(event.activityId());
    }

    @TransactionalEventListener
    void on(CommentReplied event) {
        evict(event.activityId());
    }

    @TransactionalEventListener
    void on(CommentDeleted event) {
        evict(event.activityId());
    }

    private void evict(ActivityId activityId) {
        cache.evict(CacheKey.activityCounters(activityId.toString()));
    }
}
