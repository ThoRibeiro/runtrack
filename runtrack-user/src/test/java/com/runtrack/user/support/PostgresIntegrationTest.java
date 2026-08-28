package com.runtrack.user.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base des tests d'intégration : un vrai PostgreSQL avec PostGIS, la même image que la
 * production, et les migrations Flyway réellement appliquées.
 *
 * <p>Le conteneur est statique, donc démarré une fois pour toutes les classes qui en
 * héritent. Un conteneur par méthode multiplierait la durée par le nombre de tests sans
 * rien prouver de plus.
 */
@SpringBootTest(classes = UserTestApplication.class)
@ActiveProfiles("test")
public abstract class PostgresIntegrationTest {

    /** PostGIS <em>est</em> un PostgreSQL, mais Testcontainers demande de le déclarer. */
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
