package com.runtrack.api;

import static com.runtrack.api.CourseFixtures.batch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.runtrack.api.CourseFixtures.Account;
import com.runtrack.api.CourseFixtures.Run;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/**
 * L'ingestion de points vue du client, contre une vraie base : c'est le seul endroit où le
 * verrou optimiste, la clé primaire des points et la table d'idempotence sont réellement
 * exercés — trois garanties qu'aucun double en mémoire ne peut prouver.
 */
class PointIngestionApiIT extends ApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private CourseFixtures fixtures;

    @BeforeEach
    void setUpFixtures() {
        fixtures = new CourseFixtures(mvc, json);
    }

    @Test
    void aBatchOfPointsAdvancesTheStats() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = fixtures.startRun(marie);

        MvcResult ingested = fixtures.ingest(marie, run, 1, 5);

        assertThat(ingested.getResponse().getStatus()).isEqualTo(200);
        var body = json.readTree(ingested.getResponse().getContentAsString());
        assertThat(body.get("acceptedCount").asInt()).isEqualTo(5);
        assertThat(body.get("lastAcceptedSequence").asInt()).isEqualTo(5);
        assertThat(body.get("stats").get("distanceMeters").asDouble()).isGreaterThan(10);

        mvc.perform(get("/api/v1/activities/" + run.id()).header("Authorization", marie.bearer()))
                .andExpect(jsonPath("$.stats.distanceMeters").value(
                        body.get("stats").get("distanceMeters").asDouble()));
    }

    /** Sans clé, la dédup par {@code sequenceNumber} suffit à protéger les statistiques. */
    @Test
    void replayingWithoutAKeyChangesNoStatistic() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = fixtures.startRun(marie);

        double first = json.readTree(fixtures.ingest(marie, run, 1, 5)
                .getResponse().getContentAsString()).get("stats").get("distanceMeters").asDouble();
        var replay = json.readTree(fixtures.ingest(marie, run, 1, 5)
                .getResponse().getContentAsString());

        assertThat(replay.get("acceptedCount").asInt()).isZero();
        assertThat(replay.get("stats").get("distanceMeters").asDouble()).isEqualTo(first);
        assertThat(replay.get("rejected").size()).isEqualTo(5);
    }

    @Test
    void theSameKeyOnTheSameBatchReplaysTheStoredResponse() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = fixtures.startRun(marie);

        String first = fixtures.ingest(marie, run, batch(run, 1, 5), "buffer-1")
                .getResponse().getContentAsString();
        String replay = fixtures.ingest(marie, run, batch(run, 1, 5), "buffer-1")
                .getResponse().getContentAsString();

        assertThat(replay).isEqualTo(first);
        assertThat(json.readTree(replay).get("acceptedCount").asInt()).isEqualTo(5);
    }

    @Test
    void theSameKeyOnAnotherBatchIsRefused() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = fixtures.startRun(marie);
        fixtures.ingest(marie, run, batch(run, 1, 5), "buffer-1");

        mvc.perform(post("/api/v1/activities/" + run.id() + "/points")
                        .header("Authorization", marie.bearer())
                        .header("Idempotency-Key", "buffer-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batch(run, 6, 9)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    /**
     * L'exigence explicite du §4 : deux lots concurrents sur la même course. Le verrou
     * optimiste les détecte, la reprise bornée les rattrape, et aucun point n'est perdu.
     */
    @Test
    void twoConcurrentBatchesBothLandWithoutLosingAPoint() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = fixtures.startRun(marie);

        Callable<Integer> firstHalf = () -> fixtures.ingest(marie, run, 1, 20).getResponse().getStatus();
        Callable<Integer> secondHalf = () -> fixtures.ingest(marie, run, 21, 40).getResponse().getStatus();

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> statuses = pool.invokeAll(List.of(firstHalf, secondHalf));
            for (Future<Integer> status : statuses) {
                assertThat(status.get()).isEqualTo(200);
            }
        }

        var afterwards = json.readTree(fixtures.ingest(marie, run, 1, 40)
                .getResponse().getContentAsString());
        // Tout a déjà été appliqué : un rejeu complet ne trouve plus rien de neuf.
        assertThat(afterwards.get("acceptedCount").asInt()).isZero();
        assertThat(afterwards.get("lastAcceptedSequence").asInt()).isEqualTo(40);
    }

    @Test
    void anotherRunnerCannotFeedSomeoneElsesRun() throws Exception {
        Account marie = fixtures.newAccount();
        Account paul = fixtures.newAccount();
        Run run = fixtures.startRun(marie);

        mvc.perform(post("/api/v1/activities/" + run.id() + "/points")
                        .header("Authorization", paul.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batch(run, 1, 3)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACTIVITY_NOT_FOUND"));
    }

    @Test
    void aFinishedRunRefusesPoints() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = fixtures.startRun(marie);
        mvc.perform(post("/api/v1/activities/" + run.id() + "/finish")
                        .header("Authorization", marie.bearer()))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/activities/" + run.id() + "/points")
                        .header("Authorization", marie.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batch(run, 1, 3)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVITY_NOT_ACCEPTING_POINTS"));
    }

    @Test
    void ingestingWithoutBeingLoggedInIsRefused() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = fixtures.startRun(marie);

        mvc.perform(post("/api/v1/activities/" + run.id() + "/points")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batch(run, 1, 3)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anEmptyBatchIsARequestError() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = fixtures.startRun(marie);

        mvc.perform(post("/api/v1/activities/" + run.id() + "/points")
                        .header("Authorization", marie.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"points\":[]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
