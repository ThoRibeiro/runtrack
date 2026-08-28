package com.runtrack.api;

import com.runtrack.RunTrackApplication;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base des tests d'API : l'application entière, chaîne de sécurité et traduction des
 * erreurs comprises, contre un vrai PostgreSQL.
 */
@SpringBootTest(classes = RunTrackApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ApiIntegrationTest {

    private static final DockerImageName IMAGE = DockerImageName
            .parse("postgis/postgis:17-3.5")
            .asCompatibleSubstituteFor("postgres");

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(IMAGE)
            .withDatabaseName("runtrack")
            .withUsername("runtrack")
            .withPassword("runtrack");

    static {
        // Démarré une fois pour toute la JVM, et jamais arrêté par JUnit. Avec
        // @Container, le conteneur meurt à la fin de la première classe de test, alors que
        // Spring garde son contexte en cache et le réutilise : les classes suivantes se
        // retrouvent avec une source de données qui pointe vers un conteneur éteint.
        POSTGRES.start();
    }
}
