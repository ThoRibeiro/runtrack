package com.runtrack.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * La description OpenAPI, générée depuis les contrôleurs (§8).
 *
 * <p>Générée, et non écrite : un fichier tenu à la main diverge du code à la première signature
 * qui change, et personne ne s'en aperçoit avant qu'un client s'en plaigne.
 *
 * <p>Les groupes suivent les modules. C'est le seul découpage qui ait un sens ici : il est déjà
 * celui du code, des dépendances et des tables — en inventer un second pour la documentation
 * ferait exister deux cartes du même territoire.
 */
@Configuration
class OpenApiConfiguration {

    private static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI runtrackOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("RunTrack API")
                        .version("v1")
                        .description("""
                                Suivi de courses à pied en temps réel.

                                Toutes les erreurs sont rendues en `application/problem+json`
                                (RFC 9457) et portent un champ `code` métier stable, à tester de
                                préférence au statut HTTP : trois causes distinctes rendent 409.

                                La pagination est **par curseur** — jamais offset/limit : on renvoie
                                le `nextCursor` reçu à la page précédente.

                                Deux flux sont en Server-Sent Events, `GET /activities/{id}/stream`
                                et `GET /notifications/stream`. Chaque événement porte un `id:` ;
                                le renvoyer en `Last-Event-ID` à la reconnexion reprend sans trou.
                                """)
                        .contact(new Contact().name("RunTrack"))
                        .license(new License().name("Propriétaire")))
                .servers(List.of(new Server().url("/").description("Instance courante")))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("""
                                Jeton d'accès obtenu par `POST /auth/login`, valable 15 minutes.
                                Certaines lectures s'en passent : une course publique se lit sans
                                compte, et un lien de partage ouvre une course privée sans jeton.
                                """)))
                // Déclaré globalement plutôt que sur chaque méthode : l'écrasante majorité des
                // endpoints l'exige, et l'oublier sur un seul le rendrait intestable dans l'UI.
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    @Bean
    GroupedOpenApi authApi() {
        return group("1-auth", "/api/v1/auth/**");
    }

    @Bean
    GroupedOpenApi userApi() {
        return group("2-user", "/api/v1/users/me/**", "/api/v1/users");
    }

    @Bean
    GroupedOpenApi socialApi() {
        return group("3-social", "/api/v1/users/*/follow", "/api/v1/users/*/followers",
                "/api/v1/users/*/following", "/api/v1/follow-requests/**",
                "/api/v1/me/follow-requests", "/api/v1/users/*/block");
    }

    @Bean
    GroupedOpenApi courseApi() {
        return group("4-course", "/api/v1/activities/**", "/api/v1/users/*/activities");
    }

    @Bean
    GroupedOpenApi sharingApi() {
        return group("5-sharing", "/api/v1/share-links/**", "/api/v1/shared/**");
    }

    @Bean
    GroupedOpenApi engagementApi() {
        return group("6-engagement", "/api/v1/comments/**");
    }

    @Bean
    GroupedOpenApi notificationApi() {
        return group("7-notification", "/api/v1/notifications/**");
    }

    @Bean
    GroupedOpenApi feedApi() {
        return group("8-feed", "/api/v1/feed");
    }

    /** Le groupe complet, pour qui cherche un endpoint sans savoir à quel module il appartient. */
    @Bean
    GroupedOpenApi everything() {
        return group("9-tout", "/api/v1/**");
    }

    private static GroupedOpenApi group(String name, String... paths) {
        return GroupedOpenApi.builder().group(name).pathsToMatch(paths).build();
    }
}
