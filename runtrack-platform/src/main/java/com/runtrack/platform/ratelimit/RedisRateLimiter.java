package com.runtrack.platform.ratelimit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Le compteur de fenêtre, dans Dragonfly.
 *
 * <p>{@code INCR} puis, <b>au premier appel seulement</b>, {@code EXPIRE}. L'ordre importe :
 * poser le TTL à chaque incrément ferait glisser la fenêtre à chaque tentative, et un attaquant qui
 * frappe sans relâche ne verrait jamais son compteur expirer — mais ne serait jamais débloqué non
 * plus. Ici la fenêtre part au premier appel et se ferme à l'heure dite.
 *
 * <p><b>Une panne du limiteur laisse passer.</b> C'est un arbitrage explicite : refuser tout le
 * trafic parce qu'un cache est tombé transformerait une gêne en indisponibilité totale, et le
 * limiteur protège d'un abus, pas d'une faille. Le compteur {@code runtrack.ratelimit.degraded}
 * existe pour que cette dégradation se voie au lieu de se deviner.
 */
@Component
class RedisRateLimiter implements RateLimiter {

    private static final Logger LOG = LoggerFactory.getLogger(RedisRateLimiter.class);

    private final StringRedisTemplate redis;
    private final Counter rejected;
    private final Counter degraded;

    RedisRateLimiter(StringRedisTemplate redis, MeterRegistry meters) {
        this.redis = redis;
        this.rejected = Counter.builder("runtrack.ratelimit.rejected")
                .description("Appels refusés parce qu'ils dépassaient leur quota")
                .register(meters);
        this.degraded = Counter.builder("runtrack.ratelimit.degraded")
                .description("Appels laissés passer parce que le compteur était injoignable")
                .register(meters);
    }

    @Override
    public boolean tryAcquire(String key, int limit, Duration window) {
        try {
            Long count = redis.opsForValue().increment(key);
            if (count == null) {
                return true;
            }
            if (count == 1) {
                redis.expire(key, window);
            }
            if (count > limit) {
                rejected.increment();
                return false;
            }
            return true;
        } catch (RuntimeException unavailable) {
            degraded.increment();
            LOG.warn("Limiteur indisponible, appel laissé passer : {}", unavailable.getMessage());
            return true;
        }
    }
}
