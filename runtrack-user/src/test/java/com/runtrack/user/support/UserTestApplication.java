package com.runtrack.user.support;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.random.RandomGenerator;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.context.annotation.Bean;

/**
 * Le contexte minimal pour tester le module en isolation : ses seuls beans, pas
 * l'application entière.
 *
 * <p>L'horloge et la source d'aléa sont normalement fournies par {@code platform}, qui vit
 * dans l'assemblage. Les redéclarer ici évite de faire dépendre le test d'un module dont il
 * n'a pas besoin.
 *
 * <p>Les scans sont explicites : cette classe vit dans un sous-package, alors que
 * l'application réelle est à la racine et couvre tout naturellement.
 */
@SpringBootApplication(scanBasePackages = "com.runtrack.user")
@EntityScan("com.runtrack.user")
@EnableJpaRepositories("com.runtrack.user")
public class UserTestApplication {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RandomGenerator randomGenerator() {
        return new SecureRandom();
    }
}
