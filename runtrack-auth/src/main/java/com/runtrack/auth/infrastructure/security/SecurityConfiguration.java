package com.runtrack.auth.infrastructure.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * La chaîne de filtres, sans état.
 *
 * <p>Sans session serveur : chaque requête porte sa preuve. C'est ce qui permet d'ajouter
 * une instance sans réplication de sessions, et c'est la condition du fan-out multi-instance
 * du temps réel.
 *
 * <p>{@code /shared/v1/**} est public mais n'est pas pour autant sans contrôle : le porteur
 * d'un lien passe par la même {@code ActivityAccessPolicy}, sous l'identité
 * {@code Viewer.ShareLinkHolder}.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfiguration {

    /** Ce que rend une API : rien à charger, donc rien d'autorisé. */
    private static final String API_CSP = "default-src 'none'";

    /**
     * Swagger UI est la seule exception, et elle est étroite : ses fichiers viennent du service
     * lui-même — d'où {@code 'self'} partout — sauf les styles, que son bundle injecte dans la
     * page au lieu de les servir en feuille. Sans {@code 'unsafe-inline'} là, la page se charge
     * mais ne s'affiche pas.
     */
    private static final String SWAGGER_UI_CSP = "default-src 'self'; script-src 'self'; "
            + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; "
            + "connect-src 'self'; base-uri 'none'; frame-ancestors 'none'";

    /**
     * Swagger UI, dans sa propre chaîne.
     *
     * <p>C'est la seule page HTML que le service rende, et la politique de la chaîne principale
     * — {@code default-src 'none'} — lui interdirait sa feuille de style comme son bundle : une
     * page blanche, sans la moindre erreur côté serveur pour l'expliquer. Assouplir ici plutôt
     * que là-bas garde la politique stricte partout où l'API répond vraiment, et cantonne le
     * relâchement à des fichiers statiques qui ne rendent aucune donnée.
     */
    @Bean
    @Order(1)
    SecurityFilterChain swaggerUiFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/swagger-ui/**", "/swagger-ui.html")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .headers(headers -> hardenedHeaders(headers, SWAGGER_UI_CSP))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http, ViewerAuthenticationFilter viewerFilter,
            ProblemAuthenticationEntryPoint entryPoint) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/auth/v1/**").permitAll()
                        .requestMatchers("/shared/v1/**").permitAll()
                        // Avant la règle publique ci-dessous, et pas après : « /user/v1/* »
                        // capture aussi « /user/v1/me », qui exposerait l'adresse e-mail et
                        // la physiologie du compte à n'importe qui. L'ordre est la règle.
                        .requestMatchers("/user/v1/me", "/user/v1/me/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/user/v1/*").permitAll()
                        // Même piège d'ordre : « /race/v1/live » est un chemin concret que
                        // le joker ci-dessous capturerait, alors qu'il exige une identité.
                        .requestMatchers(HttpMethod.GET, "/race/v1/live").authenticated()
                        // Les lectures de courses sont ouvertes ici et tranchées par
                        // ActivityAccessPolicy : une course publique doit rester lisible sans
                        // compte, et une course fermée répond « introuvable », pas 401.
                        .requestMatchers(HttpMethod.GET, "/race/v1/*").permitAll()
                        // Le direct suit la même règle que la lecture : c'est ActivityAccessPolicy
                        // qui tranche, et une course fermée répond « introuvable » ici aussi.
                        .requestMatchers(HttpMethod.GET, "/race/v1/*/stream").permitAll()
                        // Likes et commentaires d'une course publique se lisent sans compte, comme
                        // la course elle-même. Écrire, en revanche, demande une identité.
                        .requestMatchers(HttpMethod.GET, "/race/v1/*/likes").permitAll()
                        .requestMatchers(HttpMethod.GET, "/race/v1/*/comments").permitAll()
                        .requestMatchers(HttpMethod.GET, "/user/v1/*/races").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // Les métriques : aucune donnée personnelle — les URI y sont des
                        // gabarits, `/race/v1/{id}`, jamais des identifiants — et la
                        // vraie protection est ailleurs : en production, l'actuator écoute sur un
                        // port séparé que l'ingress n'expose pas.
                        .requestMatchers("/actuator/prometheus").permitAll()
                        // La description de l'API est un contrat, pas un secret : ce qu'elle
                        // décrit reste protégé par les règles ci-dessus. L'UI qui la lit, elle,
                        // a sa propre chaîne plus haut.
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                .headers(headers -> hardenedHeaders(headers, API_CSP))
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
     * Les en-têtes de durcissement du §9, communs aux deux chaînes. Boot en pose déjà plusieurs ;
     * ceux-ci sont ceux qui manquent et qui comptent : dire au navigateur de ne rien deviner, de
     * ne pas fuiter le chemin dans le Referer, d'exiger HTTPS, et de n'exécuter que ce que la
     * politique passée en argument autorise.
     */
    private static void hardenedHeaders(HeadersConfigurer<HttpSecurity> headers, String csp) {
        headers.contentTypeOptions(withDefaults -> { })
                .referrerPolicy(policy -> policy.policy(ReferrerPolicy.NO_REFERRER))
                .httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .maxAgeInSeconds(Duration.ofDays(365).toSeconds()))
                .contentSecurityPolicy(policy -> policy.policyDirectives(csp))
                .frameOptions(frames -> frames.deny());
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
