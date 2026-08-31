package com.runtrack.platform;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.random.RandomGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * L'horloge et la source d'aléa de l'application, en beans.
 *
 * <p>Les rendre injectables est ce qui permet à ArchUnit d'interdire {@code Instant.now()}
 * partout ailleurs, et de figer le temps en test avec {@code Clock.fixed}.
 */
@Configuration
@EnableConfigurationProperties({com.runtrack.platform.cache.CacheProperties.class,
        com.runtrack.platform.ratelimit.RateLimitProperties.class})
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
