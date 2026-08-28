package com.runtrack.auth.internal.infra.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * La configuration des jetons d'accès.
 *
 * <p>Les clés sont des chemins, jamais des valeurs en dur : elles arrivent par variables
 * d'environnement ou par volume monté. En l'absence de clés — et uniquement sous le profil
 * {@code local} — une paire éphémère est tirée au démarrage, ce qui invalide les jetons à
 * chaque redémarrage. C'est voulu : cela rend impossible d'expédier en production une
 * configuration sans clés sans s'en apercevoir.
 *
 * @param privateKeyLocation emplacement de la clé privée RSA au format PEM PKCS#8
 * @param publicKeyLocation emplacement de la clé publique RSA au format PEM X.509
 * @param accessTokenLifetime durée de vie d'un jeton d'accès
 * @param issuer valeur de la revendication {@code iss}
 */
@ConfigurationProperties(prefix = "runtrack.auth.jwt")
public record JwtProperties(
        String privateKeyLocation,
        String publicKeyLocation,
        Duration accessTokenLifetime,
        String issuer) {

    public JwtProperties {
        accessTokenLifetime = accessTokenLifetime == null ? Duration.ofMinutes(15) : accessTokenLifetime;
        issuer = issuer == null ? "runtrack" : issuer;
    }

    public boolean hasKeyPair() {
        return privateKeyLocation != null && !privateKeyLocation.isBlank()
                && publicKeyLocation != null && !publicKeyLocation.isBlank();
    }
}
