package com.runtrack.platform.cache;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * L'accès au cache applicatif, vu des décorateurs de ports.
 *
 * <p>Volontairement pauvre : lire, écrire, invalider. Les cas d'usage ne le voient jamais —
 * ils ne connaissent que leurs ports, et c'est un décorateur qui s'intercale.
 */
public interface CacheGateway {

    <T> Optional<T> get(String key, Class<T> type);

    <T> void put(String key, T value, Duration ttl);

    void evict(String key);

    void evictAll(Collection<String> keys);

    /**
     * Lit, ou charge et mémorise.
     *
     * <p>Une panne du cache n'est jamais propagée : le chargeur est appelé et
     * l'application répond. Un cache indisponible dégrade la latence, il ne doit pas
     * rendre le service indisponible.
     */
    <T> T getOrLoad(String key, Class<T> type, Duration ttl, Supplier<T> loader);
}
