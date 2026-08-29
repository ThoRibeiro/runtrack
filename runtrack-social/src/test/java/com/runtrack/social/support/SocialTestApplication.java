package com.runtrack.social.support;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Contexte des tests d'intégration de {@code social}. {@code user} est scanné aussi :
 * la portée du compte suivi décide de l'état initial de l'abonnement, et la simuler
 * reviendrait à ne plus tester ce chemin.
 */
@SpringBootApplication(scanBasePackages = {"com.runtrack.social", "com.runtrack.user", "com.runtrack.platform"})
@EntityScan({"com.runtrack.social", "com.runtrack.user"})
@EnableJpaRepositories({"com.runtrack.social", "com.runtrack.user"})
public class SocialTestApplication {
}
