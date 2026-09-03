package com.runtrack.auth.infrastructure.mail;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Le réglage des courriels d'authentification.
 *
 * <p>{@code baseUrl} est celle du <b>front</b>, pas celle de l'API : le lien reçu ouvre un
 * écran, et cet écran appelle ensuite {@code /auth/v1/verify-email}. Mettre l'API ici enverrait
 * la personne sur du JSON.
 *
 * <p>Les deux fabriques de liens vivent ici et non dans les mailers : la forme du lien est la
 * même qu'on le journalise ou qu'on l'expédie, et deux copies divergent au premier changement
 * de route.
 */
@ConfigurationProperties("runtrack.mail")
public record AuthMailProperties(String provider, String from, String baseUrl) {

    public AuthMailProperties {
        provider = provider == null || provider.isBlank() ? "logging" : provider;
        from = from == null || from.isBlank() ? "no-reply@runtrack.app" : from;
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://localhost:8081" : baseUrl;
        // Une base avec barre finale donnerait `…//verify-email` : accepté par la plupart des
        // serveurs, mais pas par le routeur du front.
        baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public String emailVerificationLink(String secret) {
        return baseUrl + "/verify-email?token=" + encoded(secret);
    }

    public String passwordResetLink(String secret) {
        return baseUrl + "/reset-password?token=" + encoded(secret);
    }

    private static String encoded(String secret) {
        return URLEncoder.encode(secret, StandardCharsets.UTF_8);
    }
}
