package com.runtrack.platform.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisRateLimiterTest {

    /** Un compteur injoignable, sans conteneur : ce chemin ne se parcourt pas par hasard. */
    private static final class UnreachableRedis extends StringRedisTemplate {

        @Override
        public ValueOperations<String, String> opsForValue() {
            throw new IllegalStateException("Dragonfly injoignable");
        }
    }

    /**
     * Une panne du limiteur laisse passer.
     *
     * <p>Arbitrage explicite : refuser tout le trafic parce qu'un cache est tombé transformerait
     * une gêne en indisponibilité totale, et le limiteur protège d'un abus, pas d'une faille. La
     * dégradation se compte, pour qu'elle se voie au lieu de se deviner.
     */
    @Test
    void anUnreachableCounterLetsTheCallThrough() {
        var meters = new SimpleMeterRegistry();
        var limiter = new RedisRateLimiter(new UnreachableRedis(), meters);

        assertThat(limiter.tryAcquire("login:ip:1.2.3.4", 5, Duration.ofMinutes(1))).isTrue();
        assertThat(meters.counter("runtrack.ratelimit.degraded").count()).isEqualTo(1);
    }
}
