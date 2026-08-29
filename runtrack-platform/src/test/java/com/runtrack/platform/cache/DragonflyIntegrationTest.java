package com.runtrack.platform.cache;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base des tests de cache, contre <b>Dragonfly</b> et non Redis.
 *
 * <p>Dragonfly parle le protocole RESP mais n'est pas Redis à cent pour cent. Tester contre
 * une image Redis validerait des commandes qui peuvent se comporter autrement en
 * production — c'est le genre d'écart qui ne se découvre qu'au déploiement.
 */
@SpringBootTest(classes = DragonflyIntegrationTest.CacheTestApplication.class)
public abstract class DragonflyIntegrationTest {

    private static final int RESP_PORT = 6379;

    static final GenericContainer<?> DRAGONFLY = new GenericContainer<>(
            DockerImageName.parse("docker.dragonflydb.io/dragonflydb/dragonfly:v1.40.1"))
            .withExposedPorts(RESP_PORT)
            .withCommand("--logtostderr");

    static {
        // Singleton : @Container l'arrêterait entre deux classes alors que Spring garde
        // son contexte en cache.
        DRAGONFLY.start();
    }

    @DynamicPropertySource
    static void dragonflyProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", DRAGONFLY::getHost);
        registry.add("spring.data.redis.port", () -> DRAGONFLY.getMappedPort(RESP_PORT));
    }

    @SpringBootApplication(scanBasePackages = "com.runtrack.platform")
    static class CacheTestApplication {
    }
}
