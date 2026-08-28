package com.runtrack.social.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Base des tests d'intégration de {@code social}. */
@SpringBootTest(classes = SocialTestApplication.class)
@ActiveProfiles("test")
public abstract class SocialIntegrationTest {

    private static final DockerImageName IMAGE = DockerImageName
            .parse("postgis/postgis:17-3.5")
            .asCompatibleSubstituteFor("postgres");

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(IMAGE)
            .withDatabaseName("runtrack")
            .withUsername("runtrack")
            .withPassword("runtrack");

    static {
        // Démarré une fois pour toute la JVM : @Container l'arrêterait à la fin de la
        // première classe, alors que Spring réutilise son contexte mis en cache.
        POSTGRES.start();
    }
}
