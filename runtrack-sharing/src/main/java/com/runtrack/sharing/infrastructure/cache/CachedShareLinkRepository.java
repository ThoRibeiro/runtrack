package com.runtrack.sharing.infrastructure.cache;

import com.runtrack.platform.cache.CacheGateway;
import com.runtrack.platform.cache.CacheKey;
import com.runtrack.platform.cache.CacheProperties;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.sharing.usecases.model.link.ShareLink;
import com.runtrack.sharing.usecases.model.link.ShareLinkId;
import com.runtrack.sharing.usecases.port.ShareLinkRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Le décorateur de cache des liens de partage.
 *
 * <p>Une seule lecture est cachée, et c'est la plus chaude du module : la résolution d'un jeton.
 * Elle a lieu à <b>chaque</b> requête sur {@code /shared/v1/**}, y compris sur chaque événement d'un
 * flux SSE reconnecté, et sur un chemin public — donc sans compte à qui imputer l'abus.
 *
 * <p>Les autres lectures — lister les liens d'une course, en charger un par son identifiant — ne
 * le sont pas : elles servent un écran de gestion qu'on ouvre rarement, et cacher une donnée peu
 * lue n'achète rien qu'une invalidation de plus à tenir juste.
 *
 * <p><b>Ce qui est caché ne décide de rien.</b> Le lien mis en cache porte ses propres
 * {@code expiresAt} et {@code revokedAt}, et c'est le domaine qui tranche à la lecture : un lien
 * qui expire pendant qu'il est en cache cesse de fonctionner à la seconde près, sans attendre son
 * éviction. Seule la <b>révocation</b> demande une invalidation, parce qu'elle change la valeur.
 */
@Component
@Primary
class CachedShareLinkRepository implements ShareLinkRepository {

    private final ShareLinkRepository delegate;
    private final CacheGateway cache;
    private final CacheProperties properties;

    CachedShareLinkRepository(@Qualifier("jdbcShareLinkRepository") ShareLinkRepository delegate,
            CacheGateway cache, CacheProperties properties) {

        this.delegate = delegate;
        this.cache = cache;
        this.properties = properties;
    }

    @Override
    public ShareLink save(ShareLink link) {
        ShareLink saved = delegate.save(link);
        // À la création comme à la révocation : dans le premier cas il n'y a rien à évincer, dans
        // le second c'est indispensable. Distinguer les deux coûterait un `if` pour économiser un
        // aller-retour sur un geste rare.
        cache.evict(CacheKey.shareToken(saved.tokenHash()));
        return saved;
    }

    @Override
    public Optional<ShareLink> findByTokenHash(String tokenHash) {
        // Optional.ofNullable et non un cache d'absence : un jeton inconnu n'a pas de valeur à
        // mémoriser, et en cacher une permettrait à qui tâtonne de peupler le cache à volonté.
        return Optional.ofNullable(cache.getOrLoad(
                CacheKey.shareToken(tokenHash), ShareLink.class, properties.shareTokenTtl(),
                () -> delegate.findByTokenHash(tokenHash).orElse(null)));
    }

    @Override
    public Optional<ShareLink> findById(ShareLinkId id) {
        return delegate.findById(id);
    }

    @Override
    public List<ShareLink> ofActivity(ActivityId activityId) {
        return delegate.ofActivity(activityId);
    }

    @Override
    public void recordView(ShareLinkId id, Instant at) {
        // Le compteur s'incrémente en base sans passer par le cache : la valeur mise en cache
        // porte un compteur qui vieillit, mais personne ne s'en sert pour décider quoi que ce
        // soit — seul l'écran de gestion l'affiche, et il lit la base.
        delegate.recordView(id, at);
    }
}
