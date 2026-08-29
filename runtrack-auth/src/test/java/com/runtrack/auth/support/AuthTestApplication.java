package com.runtrack.auth.support;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Le contexte des tests d'intégration d'{@code auth}.
 *
 * <p>{@code user} est scanné aussi : {@code auth} en dépend réellement pour créer le
 * profil, et le remplacer par un double ici ne testerait plus le chemin qui tourne en
 * production.
 */
@SpringBootApplication(scanBasePackages = {"com.runtrack.auth", "com.runtrack.user", "com.runtrack.platform"})
@EntityScan({"com.runtrack.auth", "com.runtrack.user"})
@EnableJpaRepositories({"com.runtrack.auth", "com.runtrack.user"})
public class AuthTestApplication {
}
