package com.runtrack.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * Les deux endpoints du §8 qui manquaient : la photo de profil et le bilan personnel.
 *
 * <p>Le bilan est servi par {@code course} bien que son URL commence par {@code /users/me} — les
 * courses lui appartiennent, et faire dépendre {@code user} de {@code course} fermerait un cycle.
 * Ce test le vérifie depuis l'extérieur, où seul compte ce que le client obtient.
 */
class ProfileAndStatsApiIT extends ApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private CourseFixtures fixtures;

    @BeforeEach
    void setUpFixtures() {
        fixtures = new CourseFixtures(mvc, json);
    }

    private void finish(Account owner, Run run) throws Exception {
        mvc.perform(post("/race/v1/" + run.id() + "/finish")
                .header("Authorization", owner.bearer())).andExpect(status().isNoContent());
    }

    /**
     * La photo suit la même règle que le reste du profil : un compte non confirmé ne publie rien.
     *
     * <p>C'est tout ce qu'un test d'API peut vérifier ici : aucun test d'intégration ne sait
     * confirmer une adresse — le jeton part par courriel, que la suite ne lit pas. Que la photo se
     * change <em>seule</em>, sans emporter le nom ni la biographie, est vérifié dans
     * {@code UserTest}, où l'on peut construire un compte actif.
     */
    @Test
    void changingTheAvatarNeedsAConfirmedAccount() throws Exception {
        Account marie = fixtures.newAccount();

        mvc.perform(put("/user/v1/me/avatar")
                        .header("Authorization", marie.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatarUrl\":\"https://cdn.example.com/marie.jpg\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));
    }

    @Test
    void anAvatarUrlLongerThanTheColumnIsRefused() throws Exception {
        Account marie = fixtures.newAccount();

        mvc.perform(put("/user/v1/me/avatar")
                        .header("Authorization", marie.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatarUrl\":\"%s\"}".formatted("h".repeat(2_001))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void changingAnAvatarNeedsAnAccount() throws Exception {
        mvc.perform(put("/user/v1/me/avatar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatarUrl\":\"https://cdn.example.com/x.jpg\"}"))
                .andExpect(status().isUnauthorized());
    }

    /** Le bilan additionne les courses terminées, et rend le détail par type à côté du total. */
    @Test
    void theRunnerTotalsAddUpFinishedRuns() throws Exception {
        Account marie = fixtures.newAccount();
        Run first = fixtures.startRun(marie);
        fixtures.ingest(marie, first, 1, 20);
        finish(marie, first);
        Run second = fixtures.startRun(marie);
        fixtures.ingest(marie, second, 1, 20);
        finish(marie, second);

        MvcResult read = mvc.perform(get("/user/v1/me/stats?period=ALL")
                        .header("Authorization", marie.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("ALL"))
                // ALL n'a pas de borne inférieure : en inventer une serait mentir.
                .andExpect(jsonPath("$.since").doesNotExist())
                .andExpect(jsonPath("$.activityCount").value(2))
                .andExpect(jsonPath("$.byType[0].type").value("RUN"))
                .andExpect(jsonPath("$.byType[0].activityCount").value(2))
                .andReturn();

        assertThat(json.readTree(read.getResponse().getContentAsString())
                .get("distanceMeters").asDouble()).isPositive();
    }

    /** Une course en cours n'a pas de total : elle n'entre pas dans le bilan. */
    @Test
    void aRunningActivityIsNotCountedYet() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = fixtures.startRun(marie);
        fixtures.ingest(marie, run, 1, 20);

        mvc.perform(get("/user/v1/me/stats?period=ALL")
                        .header("Authorization", marie.bearer()))
                .andExpect(jsonPath("$.activityCount").value(0));

        finish(marie, run);
        mvc.perform(get("/user/v1/me/stats?period=ALL")
                        .header("Authorization", marie.bearer()))
                .andExpect(jsonPath("$.activityCount").value(1));
    }

    /** Le bilan est le sien : les courses d'un autre n'y entrent jamais. */
    @Test
    void nobodyElsesRunsLeakIntoTheTotals() throws Exception {
        Account marie = fixtures.newAccount();
        Account paul = fixtures.newAccount();
        Run run = fixtures.startRun(marie);
        fixtures.ingest(marie, run, 1, 20);
        finish(marie, run);

        mvc.perform(get("/user/v1/me/stats?period=ALL")
                        .header("Authorization", paul.bearer()))
                .andExpect(jsonPath("$.activityCount").value(0));
    }

    /** La période demandée revient avec la borne réellement appliquée. */
    @Test
    void aCalendarPeriodComesBackWithItsBoundary() throws Exception {
        Account marie = fixtures.newAccount();

        mvc.perform(get("/user/v1/me/stats?period=MONTH&zone=Europe/Paris")
                        .header("Authorization", marie.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("MONTH"))
                .andExpect(jsonPath("$.since").exists());
    }

    /** Sans paramètre, le mois : c'est ce qu'un écran de bilan affiche par défaut. */
    @Test
    void theMonthIsTheDefaultPeriod() throws Exception {
        Account marie = fixtures.newAccount();

        mvc.perform(get("/user/v1/me/stats").header("Authorization", marie.bearer()))
                .andExpect(jsonPath("$.period").value("MONTH"));
    }

    @Test
    void anUnknownPeriodOrZoneIsARequestError() throws Exception {
        Account marie = fixtures.newAccount();

        mvc.perform(get("/user/v1/me/stats?period=DECENNIE")
                        .header("Authorization", marie.bearer()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_VALUE"));

        mvc.perform(get("/user/v1/me/stats?zone=Mars/Olympus")
                        .header("Authorization", marie.bearer()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_VALUE"));
    }

    @Test
    void theTotalsNeedAnAccount() throws Exception {
        mvc.perform(get("/user/v1/me/stats")).andExpect(status().isUnauthorized());
    }
}
