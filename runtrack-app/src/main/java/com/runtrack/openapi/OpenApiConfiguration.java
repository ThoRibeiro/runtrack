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

    /**
     * Les préfixes exposés, dans l'ordre du sélecteur de groupes. {@code /shared/v1/**} n'y est
     * pas : ce chemin n'a pas de contrôleur — le filtre de {@code sharing} le réachemine vers
     * {@code /race/v1} — et springdoc ne peut donc rien en décrire.
     */
    private static final String[] ALL_PREFIXES = {
        "/auth/v1/**", "/user/v1", "/user/v1/**", "/race/v1", "/race/v1/**", "/comment/v1/**",
        "/share-link/v1/**", "/notification/v1", "/notification/v1/**", "/feed/v1", "/feed/v1/**",
    };

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

                                Deux flux sont en Server-Sent Events, `GET /race/v1/{id}/stream`
                                et `GET /notification/v1/stream`. Chaque événement porte un `id:` ;
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
                                Jeton d'accès obtenu par `POST /auth/v1/login`, valable 15 minutes.
                                Certaines lectures s'en passent : une course publique se lit sans
                                compte, et un lien de partage ouvre une course privée sans jeton.
                                """)))
                // Déclaré globalement plutôt que sur chaque méthode : l'écrasante majorité des
                // endpoints l'exige, et l'oublier sur un seul le rendrait intestable dans l'UI.
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    @Bean
    GroupedOpenApi authApi() {
        return group("1-auth", "/auth/v1/**");
    }

    /**
     * Tout ce qui pend d'un compte : profil, graphe social, appareils, préférences, bilans, et
     * les courses d'un coureur. Le groupe suit le préfixe, pas le module : {@code user},
     * {@code social}, {@code course} et {@code notification} servent tous les quatre du
     * {@code /user/v1}, et un lecteur qui cherche « ce que je peux faire sur un compte » n'a pas
     * à savoir lequel répond.
     */
    @Bean
    GroupedOpenApi userApi() {
        return group("2-user", "/user/v1", "/user/v1/**");
    }

    @Bean
    GroupedOpenApi raceApi() {
        return group("3-race", "/race/v1", "/race/v1/**");
    }

    @Bean
    GroupedOpenApi commentApi() {
        return group("4-comment", "/comment/v1/**");
    }

    @Bean
    GroupedOpenApi shareLinkApi() {
        return group("5-share-link", "/share-link/v1/**");
    }

    @Bean
    GroupedOpenApi notificationApi() {
        return group("6-notification", "/notification/v1", "/notification/v1/**");
    }

    @Bean
    GroupedOpenApi feedApi() {
        return group("7-feed", "/feed/v1", "/feed/v1/**");
    }

    /** Le groupe complet, pour qui cherche un endpoint sans savoir de quelle ressource il pend. */
    @Bean
    GroupedOpenApi everything() {
        return group("8-tout", ALL_PREFIXES);
    }

    private static GroupedOpenApi group(String name, String... paths) {
        return GroupedOpenApi.builder().group(name).pathsToMatch(paths).build();
    }
}
