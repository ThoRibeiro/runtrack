package com.runtrack.auth.infrastructure.security;

import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Qui a le droit de signer un jeton d'accès.
 *
 * <p>Deux mondes, un seul à la fois, choisis par {@code runtrack.auth.provider} :
 * <ul>
 *   <li>{@code local} — la paire RSA de l'application, celle que {@code AuthController}
 *       utilise pour émettre. C'est le défaut ;</li>
 *   <li>{@code keycloak} — le realm, dont on ne connaît que les clés publiques.</li>
 * </ul>
 *
 * <p>Le reste de l'application ne voit pas la différence : {@link ViewerAuthenticationFilter}
 * reçoit un {@code JwtDecoder} et rend un {@code Viewer}. C'est tout l'intérêt de faire porter
 * la bascule par ce seul bean.
 *
 * <p><b>Une propriété, pas un profil.</b> Un profil dédié obligerait à quitter le profil local
 * pour exercer un vrai jeton de realm ; ici les deux se testent sur le même poste, comme pour
 * {@code runtrack.mail.provider}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KeycloakProperties.class)
class JwtDecoderConfiguration {

    /** Les jetons émis par l'application elle-même, vérifiés avec sa propre clé publique. */
    @Bean
    @ConditionalOnProperty(name = "runtrack.auth.provider", havingValue = "local",
            matchIfMissing = true)
    JwtDecoder localJwtDecoder(JWKSource<SecurityContext> jwkSource) throws Exception {
        RSAKey key = (RSAKey) jwkSource
                .get(new JWKSelector(new JWKMatcher.Builder().build()), null)
                .getFirst();
        return NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
    }

    /**
     * Les jetons du realm.
     *
     * <p><b>Le JWKS est lu à la première validation, pas au démarrage.</b>
     * {@code withIssuerLocation} irait chercher la configuration OIDC pendant l'amorçage :
     * l'API ne démarrerait plus si Keycloak est encore en train de se lever, ou tombé. En
     * partant de l'URI des clés, l'application démarre seule et n'a besoin du realm qu'au
     * moment où un jeton se présente.
     *
     * <p>L'{@code iss} est vérifié explicitement, puisque personne ne l'a fait pour nous : sans
     * ce validateur, un jeton signé par un autre realm de la même instance passerait.
     */
    @Bean
    @ConditionalOnProperty(name = "runtrack.auth.provider", havingValue = "keycloak")
    JwtDecoder keycloakJwtDecoder(KeycloakProperties properties) {
        if (!properties.isConfigured()) {
            throw new IllegalStateException(
                    "runtrack.auth.provider=keycloak sans runtrack.auth.keycloak.issuer-uri");
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuerUri()));
        return decoder;
    }
}
