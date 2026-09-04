package com.runtrack.auth.infrastructure.mail;

import com.runtrack.auth.usecases.port.AuthMailer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Journalise le lien au lieu de l'expédier, pour développer sans serveur de messagerie.
 *
 * <p>Le choix se fait par propriété et non par profil, comme pour le push : on bascule alors sur
 * un vrai envoi <em>sans quitter le profil {@code local}</em>, ce qui est précisément ce qu'on
 * veut pour vérifier le parcours. Le cas d'usage, lui, ne connaît que le port.
 *
 * <p>C'est le défaut : une propriété absente journalise, elle n'expédie pas. Se tromper de sens
 * enverrait de vrais courriels depuis un poste de développement.
 */
@Component
@ConditionalOnProperty(name = "runtrack.mail.provider", havingValue = "logging", matchIfMissing = true)
class LoggingAuthMailer implements AuthMailer {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingAuthMailer.class);

    private final AuthMailProperties properties;

    LoggingAuthMailer(AuthMailProperties properties) {
        this.properties = properties;
    }

    @Override
    public void sendEmailVerification(String emailAddress, String secret) {
        LOG.info("Confirmation d'adresse pour {} : {}",
                emailAddress, properties.emailVerificationLink(secret));
    }

    @Override
    public void sendPasswordReset(String emailAddress, String secret) {
        LOG.info("Réinitialisation pour {} : {}",
                emailAddress, properties.passwordResetLink(secret));
    }
}
