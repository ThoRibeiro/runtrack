package com.runtrack.social.infrastructure.cache;

import com.runtrack.platform.cache.CacheGateway;
import com.runtrack.platform.cache.CacheKey;
import com.runtrack.shared.id.UserId;
import com.runtrack.social.event.FollowAccepted;
import com.runtrack.social.event.FollowDropped;
import com.runtrack.social.event.UserBlocked;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Purge les listes d'abonnés et d'abonnements dès que le graphe bouge.
 *
 * <p>Les <em>deux</em> comptes sont invalidés à chaque fois : un abonnement change la liste
 * d'abonnés de l'un et la liste d'abonnements de l'autre. N'en purger qu'un laisserait
 * l'autre servir une vue périmée jusqu'à expiration.
 * */
@Component
class SocialCacheInvalidation {

    private final CacheGateway cache;

    SocialCacheInvalidation(CacheGateway cache) {
        this.cache = cache;
    }

    @TransactionalEventListener
    void on(FollowAccepted event) {
        evictPair(event.followerId(), event.followeeId());
    }

    @TransactionalEventListener
    void on(FollowDropped event) {
        evictPair(event.followerId(), event.followeeId());
    }

    @TransactionalEventListener
    void on(UserBlocked event) {
        evictPair(event.blockerId(), event.blockedId());
    }

    private void evictPair(UserId one, UserId other) {
        cache.evictAll(List.of(
                CacheKey.followers(one.toString()), CacheKey.followees(one.toString()),
                CacheKey.followers(other.toString()), CacheKey.followees(other.toString())));
    }
}
