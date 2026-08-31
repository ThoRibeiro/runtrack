package com.runtrack.platform.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * L'implémentation Dragonfly du cache, via le protocole RESP.
 *
 * <p>Trois choix qui méritent d'être dits :
 *
 * <p><b>Sérialisation JSON explicite</b>, jamais la sérialisation Java native : cette
 * dernière lie le contenu du cache aux classes exactes de l'application, ce qui casse au
 * premier renommage, et désérialiser du binaire arbitraire est une faille connue.
 *
 * <p><b>TTL avec jitter</b> : mille clés écrites dans la même seconde expirent dans la même
 * seconde, et toute la charge repart en base d'un coup. Un TTL bruité étale les
 * expirations.
 *
 * <p><b>Dégradation gracieuse</b> : toute erreur du cache est journalisée et avalée. Un
 * cache injoignable coûte de la latence, il ne doit pas transformer une lecture en panne.
 */
@Component
class RedisCacheGateway implements CacheGateway {

    private static final Logger LOG = LoggerFactory.getLogger(RedisCacheGateway.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final MeterRegistry meters;
    private final RandomGenerator random;
    private final CacheProperties properties;

    RedisCacheGateway(StringRedisTemplate redis, ObjectMapper json, MeterRegistry meters,
            RandomGenerator random, CacheProperties properties) {
        this.redis = redis;
        this.json = json;
        this.meters = meters;
        this.random = random;
        this.properties = properties;
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        Timer.Sample sample = Timer.start(meters);
        try {
            String raw = redis.opsForValue().get(key);
            sample.stop(meters.timer("runtrack.cache.read", "cache", nameOf(key)));
            if (raw == null) {
                count("runtrack.cache.miss", key);
                return Optional.empty();
            }
            count("runtrack.cache.hit", key);
            return Optional.of(json.readValue(raw, type));
        } catch (RuntimeException e) {
            // Une valeur illisible est traitée comme absente : elle vient d'une version
            // antérieure du format, et la recharger coûte moins qu'un incident.
            degraded(key, e);
            return Optional.empty();
        }
    }

    @Override
    public <T> void put(String key, T value, Duration ttl) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            redis.opsForValue().set(key, json.writeValueAsString(value), withJitter(ttl));
        } catch (RuntimeException e) {
            degraded(key, e);
        }
    }

    @Override
    public void evict(String key) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            redis.delete(key);
            count("runtrack.cache.eviction", key);
        } catch (RuntimeException e) {
            degraded(key, e);
        }
    }

    @Override
    public void evictAll(Collection<String> keys) {
        if (!properties.isEnabled() || keys.isEmpty()) {
            return;
        }
        try {
            redis.delete(keys);
            keys.forEach(key -> count("runtrack.cache.eviction", key));
        } catch (RuntimeException e) {
            degraded(keys.iterator().next(), e);
        }
    }

    @Override
    public <T> T getOrLoad(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        Optional<T> cached = get(key, type);
        if (cached.isPresent()) {
            return cached.get();
        }
        return recomputeOnce(key, type, ttl, loader);
    }

    /**
     * Le verrou anti-stampede du §6.
     *
     * <p>Quand l'entrée des abonnés d'un compte très suivi expire, toutes les requêtes en cours
     * constatent le manque <em>en même temps</em> et partent recalculer ensemble : c'est
     * précisément la requête la plus lourde de l'application, multipliée par le trafic. Un verrou
     * court désigne un seul recalculateur.
     *
     * <p>Les autres n'attendent pas indéfiniment : elles laissent au gagnant le temps d'écrire,
     * relisent, et <b>si le cache est encore vide, chargent quand même</b>. Bloquer serait pire
     * que le problème — un verrou perdu à cause d'une panne figerait la lecture pour toute la
     * durée de son expiration.
     */
    private <T> T recomputeOnce(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        if (!acquired(key)) {
            Optional<T> written = waitForWinner(key, type);
            if (written.isPresent()) {
                return written.get();
            }
            // Le gagnant n'a rien écrit — valeur nulle, ou panne. On charge sans mémoriser :
            // c'est à lui de renseigner l'entrée.
            return loader.get();
        }
        try {
            T loaded = loader.get();
            if (loaded != null) {
                put(key, loaded, ttl);
            }
            return loaded;
        } finally {
            release(key);
        }
    }

    private boolean acquired(String key) {
        try {
            return Boolean.TRUE.equals(
                    redis.opsForValue().setIfAbsent(lockKey(key), "1", properties.recomputeLock()));
        } catch (RuntimeException degraded) {
            // Sans verrou joignable, tout le monde recalcule : c'est le comportement d'avant, et
            // il vaut mieux que de ne rien rendre du tout.
            return true;
        }
    }

    private <T> Optional<T> waitForWinner(String key, Class<T> type) {
        try {
            Thread.sleep(properties.recomputeWait().toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
        return get(key, type);
    }

    private void release(String key) {
        try {
            redis.delete(lockKey(key));
        } catch (RuntimeException degraded) {
            // Le verrou expire seul : le pire cas est un recalcul retardé de sa durée de vie.
            LOG.debug("Verrou de recalcul non libéré sur {}", key);
        }
    }

    private static String lockKey(String key) {
        return key + ":recompute";
    }

    /**
     * Étale les expirations pour éviter que mille clés nées ensemble ne meurent ensemble.
     */
    private Duration withJitter(Duration ttl) {
        long spread = (long) (ttl.toMillis() * properties.jitterRatio());
        return spread <= 0 ? ttl : ttl.plusMillis(random.nextLong(spread + 1));
    }

    private void count(String metric, String key) {
        Counter.builder(metric).tag("cache", nameOf(key)).register(meters).increment();
    }

    private void degraded(String key, RuntimeException cause) {
        count("runtrack.cache.degraded", key);
        LOG.warn("Cache indisponible ou illisible pour {} : lecture directe en base", key, cause);
    }

    /**
     * Le nom logique du cache, sans l'identifiant : autrement chaque utilisateur créerait
     * sa propre série de métriques et ferait exploser la cardinalité.
     */
    private static String nameOf(String key) {
        String withoutPrefix = key.startsWith(CacheKey.PREFIX)
                ? key.substring(CacheKey.PREFIX.length()) : key;
        int separator = withoutPrefix.indexOf(':');
        return separator < 0 ? withoutPrefix : withoutPrefix.substring(0, separator);
    }
}
