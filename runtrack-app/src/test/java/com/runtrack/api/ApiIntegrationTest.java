package com.runtrack.api;

import com.runtrack.RunTrackApplication;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base des tests d'API : l'application entière, chaîne de sécurité et traduction des
 * erreurs comprises, contre un vrai PostgreSQL.
 *
 * <p><b>Un serveur pour de vrai, et MockMvc par-dessus.</b> Tout ce qui se joue en une
 * requête-réponse passe par MockMvc, plus rapide et plus lisible. Le SSE, lui, exige un
 * conteneur : sa réponse est écrite par deux fils à la fois, et celle de MockMvc ne le
 * supporte pas — voir {@link SseStream}. Les deux cohabitent sur le même contexte, donc
 * sans second démarrage.
 */
@SpringBootTest(
        classes = RunTrackApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ApiIntegrationTest {

    private static final DockerImageName IMAGE = DockerImageName
            .parse("postgis/postgis:17-3.5")
            .asCompatibleSubstituteFor("postgres");

    private static final int RESP_PORT = 6379;

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(IMAGE)
            .withDatabaseName("runtrack")
            .withUsername("runtrack")
            .withPassword("runtrack");

    /** Dragonfly, pas Redis : les tests d'API exercent le même couple qu'en production. */
    static final GenericContainer<?> DRAGONFLY = new GenericContainer<>(
            DockerImageName.parse("docker.dragonflydb.io/dragonflydb/dragonfly:v1.40.1"))
            .withExposedPorts(RESP_PORT)
            .withCommand("--logtostderr");

    static {
        // Démarré une fois pour toute la JVM, et jamais arrêté par JUnit. Avec
        // @Container, le conteneur meurt à la fin de la première classe de test, alors que
        // Spring garde son contexte en cache et le réutilise : les classes suivantes se
        // retrouvent avec une source de données qui pointe vers un conteneur éteint.
        POSTGRES.start();
        DRAGONFLY.start();
    }

    @DynamicPropertySource
    static void dragonflyProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", DRAGONFLY::getHost);
        registry.add("spring.data.redis.port", () -> DRAGONFLY.getMappedPort(RESP_PORT));
    }
}
