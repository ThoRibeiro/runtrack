package com.runtrack.auth.infrastructure.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Les origines autorisées, par profil (§9).
 *
 * <p>Une liste, et jamais {@code *} : un caractère générique combiné à des identifiants — ce que
 * fait toute application qui envoie un jeton — est refusé par les navigateurs, et le contourner
 * en renvoyant l'origine reçue revient à n'avoir aucune restriction.
 *
 * <p>Liste vide en production tant que le domaine du front n'est pas connu : refuser par défaut,
 * puis ouvrir ce qu'il faut.
 */
@ConfigurationProperties("runtrack.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
