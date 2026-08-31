package com.runtrack.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.runtrack.api.CourseFixtures.Account;
import com.runtrack.api.CourseFixtures.Run;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/**
 * L'émission, la révocation et la <em>résolution</em> d'un lien.
 *
 * <p>MockMvc n'exécute pas les réacheminements : il note la cible et s'arrête là. Ce qui se vérifie
 * donc ici, c'est le travail du filtre du §5.4 — reconnaître le jeton, décider, et désigner le
 * chemin de {@code course}. Que {@code course} réponde ensuite correctement à ce chemin est
 * l'affaire de {@link SharedActivityIT}, qui parle à un vrai serveur.
 */
class SharingApiIT extends ApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private CourseFixtures fixtures;

    @BeforeEach
    void setUpFixtures() {
        fixtures = new CourseFixtures(mvc, json);
    }

    private String issueLinkFor(Account owner, Run run) throws Exception {
        MvcResult created = mvc.perform(post("/api/v1/activities/" + run.id() + "/share-links")
                        .header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").exists())
                .andReturn();
        return json.readTree(created.getResponse().getContentAsString()).get("token").asText();
    }

    /** Un jeton valide désigne sa course — et rien d'autre ne se produit avant ce point. */
    @Test
    void aValidTokenIsResolvedToItsRun() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = fixtures.startRun(marie, "PRIVATE");
        String token = issueLinkFor(marie, run);

        // Sans le lien, la course privée est introuvable, même en étant connecté.
        Account paul = fixtures.newAccount();
        mvc.perform(get("/api/v1/activities/" + run.id()).header("Authorization", paul.bearer()))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/shared/" + token))
                .andExpect(forwardedUrl("/api/v1/activities/" + run.id()));
    }

    /** Le suffixe voyage tel quel : le direct d'une course partagée emprunte le même chemin. */
    @Test
    void theSuffixIsCarriedOverToCourse() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = fixtures.startRun(marie, "PRIVATE");
        String token = issueLinkFor(marie, run);

        mvc.perform(get("/api/v1/shared/" + token + "/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(forwardedUrl("/api/v1/activities/" + run.id() + "/stream"));
    }

    /** Un lien ne désigne que sa course, jamais une autre du même compte. */
    @Test
    void aLinkNeverPointsAtAnotherRun() throws Exception {
        Account marie = fixtures.newAccount();
        Run shared = fixtures.startRun(marie, "PRIVATE");
        Run secret = fixtures.startRun(marie, "PRIVATE");
        String token = issueLinkFor(marie, shared);

        mvc.perform(get("/api/v1/shared/" + token))
                .andExpect(forwardedUrl("/api/v1/activities/" + shared.id()));
        assertThat(secret.id()).isNotEqualTo(shared.id());
    }

    @Test
    void anUnknownTokenLooksExactlyLikeARevokedOne() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = fixtures.startRun(marie, "PRIVATE");
        String token = issueLinkFor(marie, run);

        MvcResult listed = mvc.perform(get("/api/v1/activities/" + run.id() + "/share-links")
                        .header("Authorization", marie.bearer()))
                .andExpect(status().isOk())
                // Le jeton n'est plus rendu : il n'existe qu'une fois, à la création.
                .andExpect(jsonPath("$.items[0].token").doesNotExist())
                .andReturn();
        String linkId = json.readTree(listed.getResponse().getContentAsString())
                .get("items").get(0).get("id").asText();

        mvc.perform(delete("/api/v1/share-links/" + linkId).header("Authorization", marie.bearer()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/shared/" + token)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/shared/jeton-invente")).andExpect(status().isNotFound());
    }

    /** Chaque ouverture est comptée : c'est ce que l'écran de gestion affiche. */
    @Test
    void everyOpeningIsCounted() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = fixtures.startRun(marie, "PRIVATE");
        String token = issueLinkFor(marie, run);

        mvc.perform(get("/api/v1/shared/" + token)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/shared/" + token)).andExpect(status().isOk());

        mvc.perform(get("/api/v1/activities/" + run.id() + "/share-links")
                        .header("Authorization", marie.bearer()))
                .andExpect(jsonPath("$.items[0].viewCount").value(2));
    }

    @Test
    void nobodySharesSomeoneElsesRun() throws Exception {
        Account marie = fixtures.newAccount();
        Account paul = fixtures.newAccount();
        Run run = fixtures.startRun(marie);

        mvc.perform(post("/api/v1/activities/" + run.id() + "/share-links")
                        .header("Authorization", paul.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACTIVITY_NOT_YOURS"));
    }
}
