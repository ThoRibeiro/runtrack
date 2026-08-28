package com.runtrack.auth.support;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.random.RandomGenerator;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Le contexte des tests d'intégration d'{@code auth}.
 *
 * <p>{@code user} est scanné aussi : {@code auth} en dépend réellement pour créer le
 * profil, et le remplacer par un double ici ne testerait plus le chemin qui tourne en
 * production.
 */
@SpringBootApplication(scanBasePackages = {"com.runtrack.auth", "com.runtrack.user"})
@EntityScan({"com.runtrack.auth", "com.runtrack.user"})
@EnableJpaRepositories({"com.runtrack.auth", "com.runtrack.user"})
public class AuthTestApplication {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RandomGenerator randomGenerator() {
        return new SecureRandom();
    }
}
