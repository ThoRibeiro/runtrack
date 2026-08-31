package com.runtrack.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.runtrack.platform.openapi.ApiFolders;
import java.util.List;
import java.util.Locale;
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
        MvcResult described = mvc.perform(get("/v3/api-docs/8-tout"))
                .andExpect(status().isOk()).andReturn();
        String document = described.getResponse().getContentAsString();

        java.nio.file.Files.createDirectories(PUBLISHED.getParent());
        java.nio.file.Files.writeString(PUBLISHED, document);

        assertThat(java.nio.file.Files.readString(PUBLISHED))
                .contains("\"openapi\"")
                .contains("/race/v1");
    }

    @Test
    void theDocumentDescribesTheApiAndItsSecurityScheme() throws Exception {
        JsonNode document = documentAt("/v3/api-docs/8-tout");

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
        JsonNode paths = documentAt("/v3/api-docs/8-tout").get("paths");

        assertThat(paths.propertyNames()).contains(
                "/auth/v1/login",
                "/user/v1/me",
                "/user/v1/{id}/follow",
                "/race/v1",
                "/race/v1/{id}/points",
                "/race/v1/{id}/stream",
                "/race/v1/{id}/track",
                "/race/v1/{id}/splits",
                "/race/v1/{id}/share-links",
                "/race/v1/{id}/likes",
                "/race/v1/{id}/comments",
                "/notification/v1",
                "/notification/v1/stream",
                "/user/v1/me/devices",
                "/feed/v1");
    }

    /** Un groupe par préfixe : on ouvre la ressource qu'on cherche, pas les cent endpoints. */
    @Test
    void eachGroupIsScopedToItsPrefix() throws Exception {
        JsonNode racePaths = documentAt("/v3/api-docs/3-race").get("paths");

        assertThat(racePaths.propertyNames()).contains("/race/v1");
        assertThat(racePaths.propertyNames()).doesNotContain("/auth/v1/login");
    }

    /**
     * Aucun groupe vide dans le sélecteur.
     *
     * <p>Un groupe dont le {@code pathsToMatch} ne colle plus aux chemins réels ne casse rien :
     * il s'ouvre, et il est vide. C'est le mode de panne d'une réindexation des URL — le groupe
     * survit au renommage, son filtre non — et rien d'autre ne l'attraperait.
     */
    @Test
    void everyDeclaredGroupDescribesAtLeastOneEndpoint() throws Exception {
        JsonNode groups = documentAt("/v3/api-docs/swagger-config").get("urls");

        assertThat(groups).isNotEmpty();
        for (JsonNode group : groups) {
            String name = group.get("name").stringValue();
            JsonNode paths = documentAt("/v3/api-docs/" + name).get("paths");
            assertThat(paths.propertyNames())
                    .describedAs("le groupe « %s » ne décrit aucun chemin", name)
                    .isNotEmpty();
        }
    }

    /**
     * Chaque opération porte un résumé, et se range dans un dossier déclaré.
     *
     * <p>Un endpoint ajouté sans {@code @Operation} n'échoue nulle part : il apparaît dans l'UI
     * sous sa seule signature technique, et sans décorateur de dossier il fonde un dossier de
     * plus, nommé d'après sa classe. Rien d'autre que ce test ne le refuse.
     */
    @Test
    void everyOperationCarriesASummaryAndAKnownFolder() throws Exception {
        List<String> folders = List.of(ApiFolders.AUTHENTICATION, ApiFolders.ACCOUNTS,
                ApiFolders.RACES, ApiFolders.NOTIFICATIONS, ApiFolders.FEED);
        JsonNode paths = documentAt("/v3/api-docs/8-tout").get("paths");

        for (String path : paths.propertyNames()) {
            for (String verb : paths.get(path).propertyNames()) {
                JsonNode operation = paths.get(path).get(verb);
                String where = verb.toUpperCase(Locale.ROOT) + " " + path;

                assertThat(operation.get("summary"))
                        .describedAs("%s n'a pas de résumé", where)
                        .isNotNull();
                assertThat(operation.get("tags").valueStream().map(JsonNode::stringValue))
                        .describedAs("%s se range dans un dossier non déclaré", where)
                        .isSubsetOf(folders);
            }
        }
    }

    /**
     * L'UI s'ouvre sur le groupe complet.
     *
     * <p>Elle n'affiche qu'un groupe à la fois et prend le premier de la liste sans qu'on lui
     * désigne un groupe primaire : la page s'ouvrirait alors sur « 1-auth », et donnerait à
     * croire que l'API se limite à l'authentification.
     */
    @Test
    void theUserInterfaceOpensOnTheCompleteGroup() throws Exception {
        JsonNode configuration = documentAt("/v3/api-docs/swagger-config");

        assertThat(configuration.get("urls.primaryName").stringValue()).isEqualTo("8-tout");
    }

    /** La description est publique : c'est un contrat, et l'ouvrir n'ouvre rien de ce qu'il décrit. */
    @Test
    void theDocumentAndItsUiAreReadableWithoutAnAccount() throws Exception {
        mvc.perform(get("/v3/api-docs/8-tout")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    /** Ce que la description promet du format d'erreur doit rester vrai. */
    @Test
    void theDescriptionMentionsTheErrorFormatAndTheCursor() throws Exception {
        String description = documentAt("/v3/api-docs/8-tout")
                .get("info").get("description").asText();

        assertThat(description).contains("problem+json").contains("nextCursor");
        mvc.perform(get("/feed/v1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").exists());
    }
}
