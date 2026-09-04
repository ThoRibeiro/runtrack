package com.runtrack.auth.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Le fournisseur d'identité, quand c'est lui qui signe les jetons.
 *
 * <p>Ces valeurs ne servent que si {@code runtrack.auth.provider} vaut {@code keycloak} : le
 * défaut reste l'émetteur local, de sorte qu'un poste sans Keycloak démarre.
 *
 * @param issuerUri l'adresse du realm, telle qu'elle apparaît dans la revendication {@code iss}
 *     des jetons — c'est cette valeur qui est vérifiée, pas celle d'où l'on a lu les clés
 */
@ConfigurationProperties(prefix = "runtrack.auth.keycloak")
public record KeycloakProperties(String issuerUri) {

    /**
     * L'emplacement des clés publiques du realm.
     *
     * <p>Déduit plutôt que configuré : OIDC impose ce chemin, et deux propriétés qui doivent
     * s'accorder finissent toujours par diverger.
     */
    public String jwkSetUri() {
        return issuerUri() + "/protocol/openid-connect/certs";
    }

    public boolean isConfigured() {
        return issuerUri != null && !issuerUri.isBlank();
    }
}
