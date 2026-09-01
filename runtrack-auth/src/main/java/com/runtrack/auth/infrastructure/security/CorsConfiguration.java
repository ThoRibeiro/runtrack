package com.runtrack.auth.infrastructure.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** La politique CORS, alimentée par le profil actif. */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
class CorsConfiguration {

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        var policy = new org.springframework.web.cors.CorsConfiguration();
        policy.setAllowedOrigins(properties.allowedOrigins());
        policy.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE"));
        policy.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key",
                "Last-Event-ID", "X-Correlation-Id"));
        // Exposé, sinon le navigateur le masque au JavaScript : c'est ce que l'utilisateur cite
        // quand il signale un problème.
        policy.setExposedHeaders(List.of("X-Correlation-Id"));
        policy.setAllowCredentials(true);
        policy.setMaxAge(Duration.ofHours(1));

        var source = new UrlBasedCorsConfigurationSource();
        // Toutes les routes, et non `/api/**` : aucune de celles de cette application ne
        // commence par ce préfixe. Elles sont montées sur `/auth/v1`, `/race/v1`, `/user/v1`,
        // `/feed/v1`, `/notification/v1`, `/comment/v1`, `/share-link/v1` et `/shared/v1`.
        //
        // Tant que le motif est resté sur `/api/**`, la politique ne s'appliquait à rien :
        // le préflight répondait 200 sans le moindre en-tête `Access-Control-*`, donc tout
        // navigateur bloquait chaque requête. Curieusement invisible, parce que `curl` ne
        // fait pas de CORS et que les tests d'intégration passent par MockMvc : seul un vrai
        // navigateur le montre.
        source.registerCorsConfiguration("/**", policy);
        return source;
    }
}
