package com.runtrack.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
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

    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final double LILLE_LATITUDE = 50.6292;
    private static final double LILLE_LONGITUDE = 3.0573;

    /** Trois mètres par seconde : une allure de coureur, que le filtre de vraisemblance laisse passer. */
    private static final double METERS_PER_SECOND = 3;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private record Account(String id, String token) {
    }

    private Account newAccount() throws Exception {
        String handle = "p" + COUNTER.incrementAndGet() + System.nanoTime() % 100_000;
        MvcResult created = mvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"handle":"%s","email":"%s@example.com","displayName":"Coureur",
                                 "password":"correcthorsebattery"}
                                """.formatted(handle, handle)))
                .andExpect(status().isCreated()).andReturn();
        String id = json.readTree(created.getResponse().getContentAsString()).get("userId").asText();

        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s@example.com","password":"correcthorsebattery"}
                                """.formatted(handle)))
                .andExpect(status().isOk()).andReturn();
        return new Account(id, json.readTree(login.getResponse().getContentAsString())
                .get("accessToken").asText());
    }

    private String bearer(Account account) {
        return "Bearer " + account.token();
    }

    private record Run(String id, Instant startedAt) {
    }

    /**
     * Le départ réel de la course est relu dans la réponse, et les points s'y accrochent.
     *
     * <p>Une date écrite en dur ne marcherait pas : le filtre écarte ce qui précède le départ
     * comme ce qui dépasse l'heure serveur, et ces deux bornes bougent à chaque exécution.
     */
    private Run startRun(Account owner) throws Exception {
        MvcResult started = mvc.perform(post("/api/v1/activities")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"RUN","title":"Sortie du matin","visibility":"PUBLIC"}
                                """))
                .andExpect(status().isCreated()).andReturn();
        var body = json.readTree(started.getResponse().getContentAsString());
        return new Run(body.get("id").asText(), Instant.parse(body.get("startedAt").asText()));
    }

    /** Trois mètres par seconde vers l'est, à partir du départ de la course. */
    private static String batch(Run run, int fromSequence, int toSequence) {
        double degreesPerMeter = 1 / (111_320d * Math.cos(Math.toRadians(LILLE_LATITUDE)));
        List<String> points = IntStream.rangeClosed(fromSequence, toSequence)
                .mapToObj(index -> """
                        {"sequenceNumber":%d,"latitude":%s,"longitude":%s,"elevation":20,
                         "recordedAt":"%s","accuracyMeters":5}
                        """.formatted(
                        index,
                        LILLE_LATITUDE,
                        LILLE_LONGITUDE + METERS_PER_SECOND * index * degreesPerMeter,
                        run.startedAt().plusSeconds(index)))
                .toList();
        return "{\"points\":[" + String.join(",", points) + "]}";
    }

    private MvcResult ingest(Account owner, Run run, String body, String key) throws Exception {
        var request = post("/api/v1/activities/" + run.id() + "/points")
                .header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (key != null) {
            request = request.header("Idempotency-Key", key);
        }
        return mvc.perform(request).andReturn();
    }

    @Test
    void aBatchOfPointsAdvancesTheStats() throws Exception {
        Account marie = newAccount();
        Run run = startRun(marie);

        MvcResult ingested = ingest(marie, run, batch(run, 1, 5), null);

        assertThat(ingested.getResponse().getStatus()).isEqualTo(200);
        var body = json.readTree(ingested.getResponse().getContentAsString());
        assertThat(body.get("acceptedCount").asInt()).isEqualTo(5);
        assertThat(body.get("lastAcceptedSequence").asInt()).isEqualTo(5);
        assertThat(body.get("stats").get("distanceMeters").asDouble()).isGreaterThan(10);

        mvc.perform(get("/api/v1/activities/" + run.id()).header("Authorization", bearer(marie)))
                .andExpect(jsonPath("$.stats.distanceMeters").value(
                        body.get("stats").get("distanceMeters").asDouble()));
    }

    /** Sans clé, la dédup par {@code sequenceNumber} suffit à protéger les statistiques. */
    @Test
    void replayingWithoutAKeyChangesNoStatistic() throws Exception {
        Account marie = newAccount();
        Run run = startRun(marie);

        double first = json.readTree(ingest(marie, run, batch(run, 1, 5), null)
                .getResponse().getContentAsString()).get("stats").get("distanceMeters").asDouble();
        var replay = json.readTree(ingest(marie, run, batch(run, 1, 5), null)
                .getResponse().getContentAsString());

        assertThat(replay.get("acceptedCount").asInt()).isZero();
        assertThat(replay.get("stats").get("distanceMeters").asDouble()).isEqualTo(first);
        assertThat(replay.get("rejected").size()).isEqualTo(5);
    }

    @Test
    void theSameKeyOnTheSameBatchReplaysTheStoredResponse() throws Exception {
        Account marie = newAccount();
        Run run = startRun(marie);

        String first = ingest(marie, run, batch(run, 1, 5), "buffer-1").getResponse().getContentAsString();
        String replay = ingest(marie, run, batch(run, 1, 5), "buffer-1").getResponse().getContentAsString();

        assertThat(replay).isEqualTo(first);
        assertThat(json.readTree(replay).get("acceptedCount").asInt()).isEqualTo(5);
    }

    @Test
    void theSameKeyOnAnotherBatchIsRefused() throws Exception {
        Account marie = newAccount();
        Run run = startRun(marie);
        ingest(marie, run, batch(run, 1, 5), "buffer-1");

        mvc.perform(post("/api/v1/activities/" + run.id() + "/points")
                        .header("Authorization", bearer(marie))
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
        Account marie = newAccount();
        Run run = startRun(marie);

        Callable<Integer> firstHalf = () -> ingest(marie, run, batch(run, 1, 20), null)
                .getResponse().getStatus();
        Callable<Integer> secondHalf = () -> ingest(marie, run, batch(run, 21, 40), null)
                .getResponse().getStatus();

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> statuses = pool.invokeAll(List.of(firstHalf, secondHalf));
            for (Future<Integer> status : statuses) {
                assertThat(status.get()).isEqualTo(200);
            }
        }

        var afterwards = json.readTree(ingest(marie, run, batch(run, 1, 40), null)
                .getResponse().getContentAsString());
        // Tout a déjà été appliqué : un rejeu complet ne trouve plus rien de neuf.
        assertThat(afterwards.get("acceptedCount").asInt()).isZero();
        assertThat(afterwards.get("lastAcceptedSequence").asInt()).isEqualTo(40);
    }

    @Test
    void anotherRunnerCannotFeedSomeoneElsesRun() throws Exception {
        Account marie = newAccount();
        Account paul = newAccount();
        Run run = startRun(marie);

        mvc.perform(post("/api/v1/activities/" + run.id() + "/points")
                        .header("Authorization", bearer(paul))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batch(run, 1, 3)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACTIVITY_NOT_FOUND"));
    }

    @Test
    void aFinishedRunRefusesPoints() throws Exception {
        Account marie = newAccount();
        Run run = startRun(marie);
        mvc.perform(post("/api/v1/activities/" + run.id() + "/finish").header("Authorization", bearer(marie)))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/activities/" + run.id() + "/points")
                        .header("Authorization", bearer(marie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batch(run, 1, 3)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVITY_NOT_ACCEPTING_POINTS"));
    }

    @Test
    void ingestingWithoutBeingLoggedInIsRefused() throws Exception {
        Account marie = newAccount();
        Run run = startRun(marie);

        mvc.perform(post("/api/v1/activities/" + run.id() + "/points")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batch(run, 1, 3)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anEmptyBatchIsARequestError() throws Exception {
        Account marie = newAccount();
        Run run = startRun(marie);

        mvc.perform(post("/api/v1/activities/" + run.id() + "/points")
                        .header("Authorization", bearer(marie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"points\":[]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
