package com.runtrack.user.support;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Le contexte minimal pour tester le module en isolation : ses beans et ceux de
 * {@code platform}, pas l'application entière.
 *
 * <p>Les scans sont explicites : cette classe vit dans un sous-package, alors que
 * l'application réelle est à la racine et couvre tout naturellement.
 */
@SpringBootApplication(scanBasePackages = {"com.runtrack.user", "com.runtrack.platform"})
@EntityScan("com.runtrack.user")
@EnableJpaRepositories("com.runtrack.user")
public class UserTestApplication {
}
