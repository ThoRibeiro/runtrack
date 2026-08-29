package com.runtrack.platform.cache;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Un ensemble d'identifiants mis en cache.
 *
 * <p>Un record enveloppe plutôt qu'une collection nue : une collection générique se
 * sérialise mal et se désérialise encore plus mal, et une enveloppe nommée laisse la place
 * à un champ supplémentaire sans changer de version de clé.
 */
public record CachedIds(List<String> ids) {

    public CachedIds {
        ids = ids == null ? List.of() : List.copyOf(ids);
    }

    public static CachedIds of(java.util.Collection<String> values) {
        return new CachedIds(List.copyOf(values));
    }

    public Set<String> asSet() {
        return new LinkedHashSet<>(ids);
    }
}
