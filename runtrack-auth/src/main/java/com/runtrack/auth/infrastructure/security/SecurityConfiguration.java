package com.runtrack.auth.infrastructure.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * La chaîne de filtres, sans état.
 *
 * <p>Sans session serveur : chaque requête porte sa preuve. C'est ce qui permet d'ajouter
 * une instance sans réplication de sessions, et c'est la condition du fan-out multi-instance
 * du temps réel.
 *
 * <p>{@code /shared/**} est public mais n'est pas pour autant sans contrôle : le porteur
 * d'un lien passe par la même {@code ActivityAccessPolicy}, sous l'identité
 * {@code Viewer.ShareLinkHolder}.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ViewerAuthenticationFilter viewerFilter,
            ProblemAuthenticationEntryPoint entryPoint) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/shared/**").permitAll()
                        // Avant la règle publique ci-dessous, et pas après : « /users/* »
                        // capture aussi « /users/me », qui exposerait l'adresse e-mail et
                        // la physiologie du compte à n'importe qui. L'ordre est la règle.
                        .requestMatchers("/api/v1/users/me", "/api/v1/users/me/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/*").permitAll()
                        // Même piège d'ordre : « /activities/live » est un chemin concret que
                        // le joker ci-dessous capturerait, alors qu'il exige une identité.
                        .requestMatchers(HttpMethod.GET, "/api/v1/activities/live").authenticated()
                        // Les lectures de courses sont ouvertes ici et tranchées par
                        // ActivityAccessPolicy : une course publique doit rester lisible sans
                        // compte, et une course fermée répond « introuvable », pas 401.
                        .requestMatchers(HttpMethod.GET, "/api/v1/activities/*").permitAll()
                        // Le direct suit la même règle que la lecture : c'est ActivityAccessPolicy
                        // qui tranche, et une course fermée répond « introuvable » ici aussi.
                        .requestMatchers(HttpMethod.GET, "/api/v1/activities/*/stream").permitAll()
                        // Likes et commentaires d'une course publique se lisent sans compte, comme
                        // la course elle-même. Écrire, en revanche, demande une identité.
                        .requestMatchers(HttpMethod.GET, "/api/v1/activities/*/likes").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/activities/*/comments").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/*/activities").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // Les métriques : aucune donnée personnelle — les URI y sont des
                        // gabarits, `/api/v1/activities/{id}`, jamais des identifiants — et la
                        // vraie protection est ailleurs : en production, l'actuator écoute sur un
                        // port séparé que l'ingress n'expose pas.
                        .requestMatchers("/actuator/prometheus").permitAll()
                        // La description de l'API est un contrat, pas un secret : ce qu'elle
                        // décrit reste protégé par les règles ci-dessus.
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .anyRequest().authenticated())
                // En-têtes de sécurité (§9). Boot en pose déjà plusieurs ; ceux-ci sont ceux qui
                // manquent et qui comptent pour une API : dire au navigateur de ne rien deviner,
                // de ne pas fuiter le chemin dans le Referer, et d'exiger HTTPS.
                .headers(headers -> headers
                        .contentTypeOptions(withDefaults -> { })
                        .referrerPolicy(policy -> policy.policy(
                                org.springframework.security.web.header.writers
                                        .ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(java.time.Duration.ofDays(365).toSeconds()))
                        // Une API ne rend jamais de HTML : une politique qui interdit tout est la
                        // bonne, et elle rend inoffensif un jour où une réponse en rendrait.
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'"))
                        .frameOptions(frames -> frames.deny()))
                // Sans argument : Spring Security va chercher le bean nommé exactement
                // `corsConfigurationSource`. L'injecter par type échouerait — Spring MVC en
                // expose un second, son introspecteur de handlers.
                .cors(org.springframework.security.config.Customizer.withDefaults())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint))
                .addFilterBefore(viewerFilter,
                        org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * La paire RSA de signature. En l'absence de clés configurées, une paire éphémère est
     * tirée au démarrage : les jetons ne survivent alors pas à un redémarrage, ce qui rend
     * une configuration incomplète immédiatement visible plutôt que silencieuse.
     */
    @Bean
    JWKSource<SecurityContext> jwkSource(JwtProperties properties, ResourceLoader loader) {
        RSAPublicKey publicKey;
        RSAPrivateKey privateKey;
        if (properties.hasKeyPair()) {
            publicKey = RsaKeys.readPublicKey(loader, properties.publicKeyLocation());
            privateKey = RsaKeys.readPrivateKey(loader, properties.privateKeyLocation());
        } else {
            KeyPair ephemeral = RsaKeys.generateEphemeral();
            publicKey = (RSAPublicKey) ephemeral.getPublic();
            privateKey = (RSAPrivateKey) ephemeral.getPrivate();
        }
        var key = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString())
                .build();
        return new ImmutableJWKSet<>(new JWKSet(key));
    }

    @Bean
    JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) throws Exception {
        RSAKey key = (RSAKey) jwkSource.get(new com.nimbusds.jose.jwk.JWKSelector(
                new com.nimbusds.jose.jwk.JWKMatcher.Builder().build()), null).getFirst();
        return NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
    }
}
