package com.runtrack.social.infrastructure.cache;

import com.runtrack.platform.cache.CacheGateway;
import com.runtrack.platform.cache.CacheKey;
import com.runtrack.platform.cache.CacheProperties;
import com.runtrack.platform.cache.CachedIds;
import com.runtrack.shared.id.UserId;
import com.runtrack.social.SocialApi;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Le décorateur de cache du graphe social.
 *
 * <p>C'est le cache qui compte le plus : la liste des abonnés est relue à chaque démarrage
 * de course pour le fan-out, et à chaque page de fil. Sur un compte très suivi, c'est la
 * requête la plus coûteuse de l'application.
 *
 * <p>Le blocage, lui, <b>n'est pas caché</b>. Il ferme un accès, et une valeur périmée de
 * cinq minutes laisserait quelqu'un voir ce qu'il ne doit plus voir pendant cinq minutes.
 * Un cache qui peut ouvrir une porte n'est pas un cache, c'est une faille avec un TTL.
 */
@Component
@Primary
class CachedSocialApi implements SocialApi {

    private final SocialApi delegate;
    private final CacheGateway cache;
    private final CacheProperties properties;

    CachedSocialApi(@Qualifier("socialApiAdapter") SocialApi delegate, CacheGateway cache,
            CacheProperties properties) {
        this.delegate = delegate;
        this.cache = cache;
        this.properties = properties;
    }

    @Override
    public Set<UserId> acceptedFollowerIds(UserId userId) {
        return read(CacheKey.followers(userId.toString()), () -> delegate.acceptedFollowerIds(userId));
    }

    @Override
    public Set<UserId> acceptedFolloweeIds(UserId userId) {
        return read(CacheKey.followees(userId.toString()), () -> delegate.acceptedFolloweeIds(userId));
    }

    @Override
    public boolean isFollowing(UserId followerId, UserId followeeId) {
        return acceptedFolloweeIds(followerId).contains(followeeId);
    }

    /** Jamais caché : voir la note de classe. */
    @Override
    public boolean isBlockedEitherWay(UserId one, UserId other) {
        return delegate.isBlockedEitherWay(one, other);
    }

    private Set<UserId> read(String key, java.util.function.Supplier<Set<UserId>> loader) {
        CachedIds cached = cache.getOrLoad(key, CachedIds.class, properties.socialTtl(),
                () -> CachedIds.of(loader.get().stream().map(UserId::toString).toList()));
        if (cached == null) {
            return Set.of();
        }
        var result = new LinkedHashSet<UserId>(cached.ids().size());
        cached.ids().forEach(id -> result.add(UserId.of(id)));
        return result;
    }
}
