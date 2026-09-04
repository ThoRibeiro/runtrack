package com.runtrack.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.env.YamlPropertySourceLoader;

/**
 * Le {@code XREAD} du direct est bloquant, et Lettuce décompte son propre délai sur cette
 * même commande. Réglés à la même valeur, Dragonfly répond à l'instant où le client
 * abandonne : chaque tour de boucle sans message lève un {@code RedisCommandTimeoutException},
 * en continu dès qu'une course est suivie.
 *
 * <p>Le commentaire dans {@code application.yml} le dit ; celui-ci l'empêche. Les deux réglages
 * sont à cinquante lignes l'un de l'autre et rien ne les relie — c'est exactement le genre de
 * paire qu'on casse en remontant l'une des deux sans penser à l'autre.
 */
class RealtimeTimeoutsTest {

    @Test
    void thePollReturnsBeforeTheRedisClientGivesUp() {
        var environment = configuration();

        Duration poll = bind(environment, "runtrack.realtime.poll-timeout");
        Duration command = bind(environment, "spring.data.redis.timeout");

        assertThat(poll)
                .as("le XREAD doit rendre la main avant que Lettuce coupe la commande")
                .isLessThan(command);
    }

    private static StandardEnvironment configuration() {
        var environment = new StandardEnvironment();
        try {
            List<org.springframework.core.env.PropertySource<?>> sources =
                    new YamlPropertySourceLoader()
                            .load("application", new ClassPathResource("application.yml"));
            sources.forEach(environment.getPropertySources()::addLast);
        } catch (Exception failure) {
            throw new IllegalStateException("application.yml illisible", failure);
        }
        return environment;
    }

    private static Duration bind(StandardEnvironment environment, String key) {
        return Binder.get(environment).bind(key, Duration.class)
                .orElseThrow(() -> new AssertionError(key + " n'est pas configuré"));
    }
}
