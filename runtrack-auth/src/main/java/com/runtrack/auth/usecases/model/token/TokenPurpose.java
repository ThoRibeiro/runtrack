package com.runtrack.auth.usecases.model.token;

import java.time.Duration;

/**
 * Ce à quoi sert un jeton à usage unique envoyé par e-mail.
 *
 * <p>La durée de vie suit l'usage : confirmer une adresse peut attendre un jour, alors
 * qu'un lien de réinitialisation reste une porte d'entrée tant qu'il vit.
 */
public enum TokenPurpose {

    EMAIL_VERIFICATION(Duration.ofDays(1)),

    PASSWORD_RESET(Duration.ofMinutes(30));

    private final Duration lifetime;

    TokenPurpose(Duration lifetime) {
        this.lifetime = lifetime;
    }

    public Duration lifetime() {
        return lifetime;
    }
}
