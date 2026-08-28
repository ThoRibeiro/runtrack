package com.runtrack.platform;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.random.RandomGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * L'horloge et la source d'aléa de l'application, en beans.
 *
 * <p>Les rendre injectables est ce qui permet à ArchUnit d'interdire {@code Instant.now()}
 * partout ailleurs, et de figer le temps en test avec {@code Clock.fixed}.
 */
@Configuration
public class PlatformConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** {@link SecureRandom} : ces valeurs servent aussi à des jetons et à l'anonymisation. */
    @Bean
    RandomGenerator randomGenerator() {
        return new SecureRandom();
    }
}
