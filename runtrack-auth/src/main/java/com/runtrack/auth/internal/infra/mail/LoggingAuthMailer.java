package com.runtrack.auth.internal.infra.mail;

import com.runtrack.auth.internal.application.port.AuthMailer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Journalise le lien au lieu de l'expédier. Actif hors production, pour développer sans
 * serveur de messagerie.
 *
 * <p>Le choix se fait par profil, pas par un {@code if} dans le cas d'usage : celui-ci ne
 * connaît que le port.
 */
@Component
@Profile("!prod")
class LoggingAuthMailer implements AuthMailer {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingAuthMailer.class);

    @Override
    public void sendEmailVerification(String emailAddress, String secret) {
        LOG.info("Confirmation d'adresse pour {} : /api/v1/auth/verify-email?token={}", emailAddress, secret);
    }

    @Override
    public void sendPasswordReset(String emailAddress, String secret) {
        LOG.info("Réinitialisation pour {} : jeton {}", emailAddress, secret);
    }
}
