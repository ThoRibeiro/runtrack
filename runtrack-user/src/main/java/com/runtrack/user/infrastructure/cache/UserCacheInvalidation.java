package com.runtrack.user.infrastructure.cache;

import com.runtrack.platform.cache.CacheGateway;
import com.runtrack.shared.id.UserId;
import com.runtrack.user.event.UserDeleted;
import com.runtrack.user.event.UserProfileUpdated;
import com.runtrack.user.event.UserRegistered;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Purge le cache d'un profil quand il change.
 *
 * <p>{@code @TransactionalEventListener} et non {@code @EventListener} : l'invalidation
 * doit avoir lieu <em>après le commit</em>. Invalider avant, c'est risquer de recharger la
 * valeur d'origine depuis une transaction encore ouverte, puis de la remettre en cache —
 * la modification serait perdue pour les lecteurs jusqu'à l'expiration.
 */
@Component
class UserCacheInvalidation {

    private final CacheGateway cache;

    UserCacheInvalidation(CacheGateway cache) {
        this.cache = cache;
    }

    /**
     * Un profil vient de naître. Sans effet à l'inscription classique — un identifiant tiré à
     * l'instant n'est dans aucun cache — mais indispensable pour une identité fédérée, dont
     * l'identifiant est celui du jeton : quelque chose a pu le demander avant qu'il existe, et
     * l'absence est cachée comme le reste.
     */
    @TransactionalEventListener
    void on(UserRegistered event) {
        evict(event.userId());
    }

    @TransactionalEventListener
    void on(UserProfileUpdated event) {
        evict(event.userId());
    }

    @TransactionalEventListener
    void on(UserDeleted event) {
        evict(event.userId());
    }

    private void evict(UserId userId) {
        cache.evictAll(CachedUserApi.keysOf(userId));
    }
}
