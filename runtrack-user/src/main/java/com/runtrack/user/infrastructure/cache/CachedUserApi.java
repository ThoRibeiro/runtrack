package com.runtrack.user.infrastructure.cache;

import com.runtrack.platform.cache.CacheGateway;
import com.runtrack.platform.cache.CacheKey;
import com.runtrack.platform.cache.CacheProperties;
import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.id.UserId;
import com.runtrack.user.FederatedProfile;
import com.runtrack.user.NewUser;
import com.runtrack.user.RunnerMass;
import com.runtrack.user.UserApi;
import com.runtrack.user.UserSummary;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Le décorateur de cache du contrat {@code user}.
 *
 * <p>Un décorateur, et non des annotations {@code @Cacheable} dispersées : le cas d'usage
 * ignore l'existence du cache et reste testable sans lui, et tout ce qui concerne le cache
 * tient dans un seul fichier de {@code infra}.
 *
 * <p><b>Ce qui est caché est le contrat public, pas l'agrégat.</b> Le cahier des charges
 * parlait de décorer le port de persistance, mais celui-ci rend un {@code User} complet —
 * adresse e-mail et physiologie comprises. Les écrire dans Dragonfly pour accélérer
 * l'affichage d'un nom serait payer un risque pour un gain nul. {@code UserSummary} et la
 * portée du compte sont exactement les données que le §6 énumère.
 *
 * <p>Les écritures ne sont pas cachées : elles traversent, et l'invalidation se fait sur
 * événement (voir {@link UserCacheInvalidation}).
 */
@Component
@Primary
class CachedUserApi implements UserApi {

    private final UserApi delegate;
    private final CacheGateway cache;
    private final CacheProperties properties;

    CachedUserApi(@Qualifier("userApiAdapter") UserApi delegate, CacheGateway cache,
            CacheProperties properties) {
        this.delegate = delegate;
        this.cache = cache;
        this.properties = properties;
    }

    /**
     * Une écriture : elle traverse. Le profil créé est publié par {@code UserRegistered}, et
     * c'est cet événement qui évince — un « profil absent » mis en cache juste avant survivrait
     * sinon jusqu'à la fin de son délai.
     */
    @Override
    public boolean ensureProfile(UserId id, FederatedProfile profile) {
        return delegate.ensureProfile(id, profile);
    }

    @Override
    public Optional<UserSummary> summary(UserId id) {
        CachedSummary cached = cache.getOrLoad(
                CacheKey.userSummary(id.toString()),
                CachedSummary.class,
                properties.userTtl(),
                () -> delegate.summary(id).map(CachedSummary::from).orElse(CachedSummary.absent()));
        return cached == null ? Optional.empty() : cached.toSummary();
    }

    /**
     * Le lot est servi clé par clé depuis le cache, et seuls les manquants partent en base.
     * Une liste d'abonnés déjà chaude ne déclenche alors aucune requête.
     */
    @Override
    public Map<UserId, UserSummary> summaries(Collection<UserId> ids) {
        var found = new HashMap<UserId, UserSummary>();
        var missing = new ArrayList<UserId>();

        for (UserId id : ids) {
            cache.get(CacheKey.userSummary(id.toString()), CachedSummary.class)
                    .flatMap(CachedSummary::toSummary)
                    .ifPresentOrElse(summary -> found.put(id, summary), () -> missing.add(id));
        }
        if (!missing.isEmpty()) {
            Map<UserId, UserSummary> loaded = delegate.summaries(missing);
            loaded.forEach((id, summary) -> cache.put(
                    CacheKey.userSummary(id.toString()), CachedSummary.from(summary), properties.userTtl()));
            found.putAll(loaded);
        }
        return Map.copyOf(found);
    }

    @Override
    public Optional<AudienceScope> accountScope(UserId id) {
        CachedScope cached = cache.getOrLoad(
                CacheKey.accountScope(id.toString()),
                CachedScope.class,
                properties.userTtl(),
                () -> new CachedScope(delegate.accountScope(id).map(Enum::name).orElse(null)));
        return cached == null ? Optional.empty() : cached.toScope();
    }

    @Override
    public boolean exists(UserId id) {
        return summary(id).isPresent();
    }

    /** Donnée sensible : elle ne transite pas par le cache. */
    @Override
    public Optional<RunnerMass> massOf(UserId id) {
        return delegate.massOf(id);
    }

    @Override
    public UserId register(NewUser newUser) {
        return delegate.register(newUser);
    }

    @Override
    public void confirmEmail(UserId id) {
        delegate.confirmEmail(id);
    }

    @Override
    public Optional<UserId> idOfEmail(String email) {
        return delegate.idOfEmail(email);
    }

    /**
     * L'absence est mémorisée comme une valeur.
     *
     * <p>Sans cela, une rafale de requêtes sur un identifiant inexistant repart en base à
     * chaque fois — c'est le chemin qu'emprunte volontiers un scanner.
     */
    record CachedSummary(String id, String handle, String displayName, String avatarUrl, boolean present) {

        static CachedSummary from(UserSummary summary) {
            return new CachedSummary(summary.id().toString(), summary.handle(),
                    summary.displayName(), summary.avatarUrl().orElse(null), true);
        }

        static CachedSummary absent() {
            return new CachedSummary(null, null, null, null, false);
        }

        Optional<UserSummary> toSummary() {
            return present
                    ? Optional.of(new UserSummary(UserId.of(id), handle, displayName,
                            Optional.ofNullable(avatarUrl)))
                    : Optional.empty();
        }
    }

    record CachedScope(String scope) {

        Optional<AudienceScope> toScope() {
            return Optional.ofNullable(scope).map(AudienceScope::valueOf);
        }
    }

    /** Rendu accessible à l'invalidation, qui vit dans le même package. */
    static List<String> keysOf(UserId id) {
        return List.of(CacheKey.userSummary(id.toString()), CacheKey.accountScope(id.toString()));
    }
}
