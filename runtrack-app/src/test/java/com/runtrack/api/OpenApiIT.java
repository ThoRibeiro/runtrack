package com.runtrack.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * La description OpenAPI est bien produite, et elle décrit l'API réelle.
 *
 * <p>Ce test existe parce qu'une documentation générée échoue en silence : springdoc n'explore les
 * contrôleurs qu'au démarrage, et une incompatibilité de version rend un document vide sans lever
 * la moindre erreur. Un `/v3/api-docs` qui répond 200 avec zéro chemin est le pire des deux mondes.
 *
 * <p>Il <b>écrit</b> aussi la description sur le disque, pour le site publié sur GitHub Pages.
 * L'écrire ici plutôt que dans une étape de build à part a une raison : le fichier publié est
 * exactement celui que les assertions ci-dessous viennent de vérifier. Une génération séparée
 * pourrait publier un document vide sans que rien ne l'attrape.
 */
class OpenApiIT extends ApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    /** Relatif à {@code runtrack-app/}, d'où les tests s'exécutent. */
    private static final java.nio.file.Path PUBLISHED =
            java.nio.file.Path.of("target", "openapi", "openapi.json");

    private JsonNode documentAt(String path) throws Exception {
        MvcResult described = mvc.perform(get(path)).andExpect(status().isOk()).andReturn();
        return json.readTree(described.getResponse().getContentAsString());
    }

    @Test
    void theDocumentIsWrittenForThePublishedSite() throws Exception {
        MvcResult described = mvc.perform(get("/v3/api-docs/9-tout"))
                .andExpect(status().isOk()).andReturn();
        String document = described.getResponse().getContentAsString();

        java.nio.file.Files.createDirectories(PUBLISHED.getParent());
        java.nio.file.Files.writeString(PUBLISHED, document);

        assertThat(java.nio.file.Files.readString(PUBLISHED))
                .contains("\"openapi\"")
                .contains("/api/v1/activities");
    }

    @Test
    void theDocumentDescribesTheApiAndItsSecurityScheme() throws Exception {
        JsonNode document = documentAt("/v3/api-docs/9-tout");

        assertThat(document.get("info").get("title").asText()).isEqualTo("RunTrack API");
        assertThat(document.get("components").get("securitySchemes").get("bearerAuth")
                .get("scheme").asText()).isEqualTo("bearer");
    }

    /**
     * Les endpoints structurants de chaque module, un par un.
     *
     * <p>Les nommer plutôt que compter : un document qui perd la moitié de ses chemins passerait
     * une assertion sur un total, pas sur cette liste.
     */
    @Test
    void everyModuleContributesItsEndpoints() throws Exception {
        JsonNode paths = documentAt("/v3/api-docs/9-tout").get("paths");

        assertThat(paths.propertyNames()).contains(
                "/api/v1/auth/login",
                "/api/v1/users/me",
                "/api/v1/users/{id}/follow",
                "/api/v1/activities",
                "/api/v1/activities/{id}/points",
                "/api/v1/activities/{id}/stream",
                "/api/v1/activities/{id}/track",
                "/api/v1/activities/{id}/splits",
                "/api/v1/activities/{id}/share-links",
                "/api/v1/activities/{id}/likes",
                "/api/v1/activities/{id}/comments",
                "/api/v1/notifications",
                "/api/v1/notifications/stream",
                "/api/v1/users/me/devices",
                "/api/v1/feed");
    }

    /** Les groupes suivent les modules : on ouvre celui qu'on cherche, pas les cent endpoints. */
    @Test
    void eachGroupIsScopedToItsModule() throws Exception {
        JsonNode coursePaths = documentAt("/v3/api-docs/4-course").get("paths");

        assertThat(coursePaths.propertyNames()).contains("/api/v1/activities");
        assertThat(coursePaths.propertyNames()).doesNotContain("/api/v1/auth/login");
    }

    /** La description est publique : c'est un contrat, et l'ouvrir n'ouvre rien de ce qu'il décrit. */
    @Test
    void theDocumentAndItsUiAreReadableWithoutAnAccount() throws Exception {
        mvc.perform(get("/v3/api-docs/9-tout")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    /** Ce que la description promet du format d'erreur doit rester vrai. */
    @Test
    void theDescriptionMentionsTheErrorFormatAndTheCursor() throws Exception {
        String description = documentAt("/v3/api-docs/9-tout")
                .get("info").get("description").asText();

        assertThat(description).contains("problem+json").contains("nextCursor");
        mvc.perform(get("/api/v1/feed"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").exists());
    }
}
