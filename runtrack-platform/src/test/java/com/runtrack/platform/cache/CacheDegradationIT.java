package com.runtrack.platform.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Ce qui se passe quand Dragonfly tombe.
 *
 * <p>C'est l'exigence qui compte le plus dans tout le cache : un cache indisponible doit
 * coûter de la latence, jamais une panne. Le prouver demande de l'arrêter vraiment — d'où
 * un conteneur propre à cette classe, qu'on peut éteindre sans gêner les autres tests.
 *
 * <p>Les méthodes sont ordonnées : le conteneur ne se rallume pas.
 */
@SpringBootTest(classes = CacheDegradationIT.DegradationTestApplication.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CacheDegradationIT {

    private static final int RESP_PORT = 6379;

    private static final GenericContainer<?> DRAGONFLY = new GenericContainer<>(
            DockerImageName.parse("docker.dragonflydb.io/dragonflydb/dragonfly:v1.40.1"))
            .withExposedPorts(RESP_PORT)
            .withCommand("--logtostderr");

    static {
        DRAGONFLY.start();
    }

    @DynamicPropertySource
    static void dragonflyProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", DRAGONFLY::getHost);
        registry.add("spring.data.redis.port", () -> DRAGONFLY.getMappedPort(RESP_PORT));
        // Sans délai court, chaque appel attendrait la coupure TCP et le test durerait des minutes.
        registry.add("spring.data.redis.timeout", () -> "500ms");
        registry.add("spring.data.redis.connect-timeout", () -> "500ms");
    }

    @Autowired
    private CacheGateway cache;

    private record Profile(String id, String displayName) {
    }

    @Test
    @Order(1)
    void servesFromCacheWhileDragonflyIsUp() {
        String key = CacheKey.PREFIX + "test:" + UUID.randomUUID();
        var loads = new AtomicInteger();

        cache.getOrLoad(key, Profile.class, Duration.ofMinutes(5),
                () -> { loads.incrementAndGet(); return new Profile("1", "Marie"); });
        cache.getOrLoad(key, Profile.class, Duration.ofMinutes(5),
                () -> { loads.incrementAndGet(); return new Profile("1", "Marie"); });

        assertThat(loads).hasValue(1);
    }

    /**
     * Dragonfly éteint, l'application continue de répondre : chaque lecture retombe sur le
     * chargeur, c'est-à-dire sur la base.
     */
    @Test
    @Order(2)
    void keepsAnsweringOnceDragonflyIsGone() {
        DRAGONFLY.stop();
        String key = CacheKey.PREFIX + "test:" + UUID.randomUUID();
        var loads = new AtomicInteger();

        Profile loaded = cache.getOrLoad(key, Profile.class, Duration.ofMinutes(5),
                () -> { loads.incrementAndGet(); return new Profile("1", "Marie"); });

        assertThat(loaded).isEqualTo(new Profile("1", "Marie"));
        assertThat(loads).hasValue(1);
    }

    @Test
    @Order(3)
    void readsWritesAndEvictionsAllStayQuiet() {
        String key = CacheKey.PREFIX + "test:" + UUID.randomUUID();

        assertThatCode(() -> {
            assertThat(cache.get(key, Profile.class)).isEmpty();
            cache.put(key, new Profile("1", "Marie"), Duration.ofMinutes(5));
            cache.evict(key);
            cache.evictAll(List.of(key, key + "-autre"));
        }).doesNotThrowAnyException();
    }

    /** Sans cache, deux lectures rechargent deux fois — c'est le prix, et il est acceptable. */
    @Test
    @Order(4)
    void everyReadFallsThroughWithoutTheCache() {
        String key = CacheKey.PREFIX + "test:" + UUID.randomUUID();
        var loads = new AtomicInteger();

        for (int i = 0; i < 3; i++) {
            cache.getOrLoad(key, Profile.class, Duration.ofMinutes(5),
                    () -> { loads.incrementAndGet(); return new Profile("1", "Marie"); });
        }

        assertThat(loads).hasValue(3);
    }

    /**
     * Le cache seul, sans le reste de {@code platform}.
     *
     * <p>La supervision du registre d'événements est délibérément hors du balayage : elle exige
     * un registre que seul l'assemblage fournit, et ce test ne parle que de cache. Un contexte de
     * test qui ramasse tout finit par échouer pour des raisons sans rapport avec ce qu'il vérifie.
     * L'horloge et l'aléa, eux, sont importés explicitement : le cache s'en sert.
     */
    @Import(com.runtrack.platform.PlatformConfiguration.class)
    @SpringBootApplication(
            scanBasePackages = "com.runtrack.platform.cache",
            // Depuis que `platform` sait lire le registre d'événements, il embarque JDBC — et
            // Boot voudrait ouvrir une source de données qu'aucun test de cache n'utilise.
            exclude = org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration.class)
    static class DegradationTestApplication {
    }
}
