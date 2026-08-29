package com.runtrack.platform.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Le cache applicatif contre une vraie instance Dragonfly. */
class RedisCacheGatewayIT extends DragonflyIntegrationTest {

    @Autowired
    private CacheGateway cache;

    @Autowired
    private StringRedisTemplate redis;

    private record Profile(String id, String displayName) {
    }

    private String uniqueKey() {
        return CacheKey.PREFIX + "test:" + UUID.randomUUID();
    }

    @Test
    void storesAndReadsBackAValue() {
        String key = uniqueKey();

        cache.put(key, new Profile("1", "Marie"), Duration.ofMinutes(5));

        assertThat(cache.get(key, Profile.class)).contains(new Profile("1", "Marie"));
    }

    @Test
    void reportsAnAbsentKeyAsEmpty() {
        assertThat(cache.get(uniqueKey(), Profile.class)).isEmpty();
    }

    /** La propriété qui justifie le cache : le second appel ne recharge plus. */
    @Test
    void theSecondCallDoesNotHitTheLoader() {
        String key = uniqueKey();
        var loads = new AtomicInteger();

        Profile first = cache.getOrLoad(key, Profile.class, Duration.ofMinutes(5),
                () -> { loads.incrementAndGet(); return new Profile("1", "Marie"); });
        Profile second = cache.getOrLoad(key, Profile.class, Duration.ofMinutes(5),
                () -> { loads.incrementAndGet(); return new Profile("1", "Marie"); });

        assertThat(loads).hasValue(1);
        assertThat(second).isEqualTo(first);
    }

    @Test
    void evictingMakesTheNextReadFresh() {
        String key = uniqueKey();
        cache.getOrLoad(key, Profile.class, Duration.ofMinutes(5), () -> new Profile("1", "Marie"));

        cache.evict(key);

        Profile reloaded = cache.getOrLoad(key, Profile.class, Duration.ofMinutes(5),
                () -> new Profile("1", "Marie D."));
        assertThat(reloaded.displayName()).isEqualTo("Marie D.");
    }

    @Test
    void evictsSeveralKeysAtOnce() {
        String one = uniqueKey();
        String other = uniqueKey();
        cache.put(one, new Profile("1", "Marie"), Duration.ofMinutes(5));
        cache.put(other, new Profile("2", "Paul"), Duration.ofMinutes(5));

        cache.evictAll(List.of(one, other));

        assertThat(cache.get(one, Profile.class)).isEmpty();
        assertThat(cache.get(other, Profile.class)).isEmpty();
    }

    @Test
    void evictingNothingIsHarmless() {
        cache.evictAll(List.of());
    }

    @Test
    void aValueExpires() throws InterruptedException {
        String key = uniqueKey();
        cache.put(key, new Profile("1", "Marie"), Duration.ofMillis(200));

        Thread.sleep(Duration.ofMillis(1_500));

        assertThat(cache.get(key, Profile.class)).isEmpty();
    }

    /** Le TTL écrit dépasse le TTL demandé : c'est le jitter anti-avalanche. */
    @Test
    void theStoredTtlCarriesJitter() {
        String key = uniqueKey();
        Duration requested = Duration.ofMinutes(10);

        cache.put(key, new Profile("1", "Marie"), requested);

        Long actual = redis.getExpire(key);
        assertThat(actual).isNotNull();
        assertThat(actual).isGreaterThanOrEqualTo(requested.toSeconds());
        assertThat(actual).isLessThanOrEqualTo((long) (requested.toSeconds() * 1.25));
    }

    /** Une valeur écrite dans un autre format est traitée comme absente, pas comme une panne. */
    @Test
    void anUnreadableValueBehavesLikeAMiss() {
        String key = uniqueKey();
        redis.opsForValue().set(key, "{ ceci n'est pas du JSON valide");

        assertThat(cache.get(key, Profile.class)).isEmpty();

        Profile reloaded = cache.getOrLoad(key, Profile.class, Duration.ofMinutes(5),
                () -> new Profile("1", "Marie"));
        assertThat(reloaded.displayName()).isEqualTo("Marie");
    }

    @Test
    void aNullFromTheLoaderIsNotStored() {
        String key = uniqueKey();

        Profile loaded = cache.getOrLoad(key, Profile.class, Duration.ofMinutes(5), () -> null);

        assertThat(loaded).isNull();
        assertThat(redis.hasKey(key)).isFalse();
    }
}
