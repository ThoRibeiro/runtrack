package com.runtrack.auth.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthMailPropertiesTest {

    /** Rien de configuré doit journaliser, jamais expédier : se tromper écrit à de vraies gens. */
    @Test
    void defaultsToLoggingRatherThanSending() {
        AuthMailProperties properties = new AuthMailProperties(null, null, null);

        assertThat(properties.provider()).isEqualTo("logging");
        assertThat(properties.from()).isEqualTo("no-reply@runtrack.app");
    }

    @Test
    void buildsTheLinksTheFrontRoutesAnswer() {
        AuthMailProperties properties =
                new AuthMailProperties("smtp", "no-reply@runtrack.app", "https://runtrack.app");

        assertThat(properties.emailVerificationLink("abc"))
                .isEqualTo("https://runtrack.app/verify-email?token=abc");
        assertThat(properties.passwordResetLink("abc"))
                .isEqualTo("https://runtrack.app/reset-password?token=abc");
    }

    /** Une barre finale donnerait `…//verify-email`, que le routeur du front ne reconnaît pas. */
    @Test
    void toleratesATrailingSlashOnTheBase() {
        AuthMailProperties properties = new AuthMailProperties("smtp", null, "https://runtrack.app/");

        assertThat(properties.emailVerificationLink("abc"))
                .isEqualTo("https://runtrack.app/verify-email?token=abc");
    }

    /**
     * Les jetons sont tirés en base64url, mais rien ne le garantit à cette couche : un `+` ou un
     * `=` recopié tel quel dans une URL arriverait déformé au serveur, et le lien serait refusé
     * sans que personne comprenne pourquoi.
     */
    @Test
    void encodesASecretThatUrlsWouldMangle() {
        AuthMailProperties properties = new AuthMailProperties("smtp", null, "https://runtrack.app");

        assertThat(properties.passwordResetLink("a+b/c=")).endsWith("token=a%2Bb%2Fc%3D");
    }
}
