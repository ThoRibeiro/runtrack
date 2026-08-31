package com.runtrack.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/**
 * Un compte, une course, un lot de points — les trois gestes que refont tous les tests de
 * {@code course}.
 *
 * <p>Écrit une fois plutôt que recopié : les points doivent être <em>plausibles</em> pour
 * traverser le filtre du domaine, et une copie de ce calcul qui dérive donne un test qui
 * échoue sans rapport avec ce qu'il vérifie.
 */
final class CourseFixtures {

    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final double LILLE_LATITUDE = 50.6292;
    private static final double LILLE_LONGITUDE = 3.0573;

    /** Trois mètres par seconde : une allure de coureur, que le filtre de vraisemblance accepte. */
    private static final double METERS_PER_SECOND = 3;

    private final MockMvc mvc;
    private final ObjectMapper json;

    CourseFixtures(MockMvc mvc, ObjectMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    record Account(String id, String token) {

        String bearer() {
            return "Bearer " + token;
        }
    }

    record Run(String id, Instant startedAt) {
    }

    Account newAccount() throws Exception {
        String handle = "f" + COUNTER.incrementAndGet() + System.nanoTime() % 100_000;
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

    Run startRun(Account owner) throws Exception {
        return startRun(owner, "PUBLIC");
    }

    /**
     * Le départ réel est relu dans la réponse, et les points s'y accrochent.
     *
     * <p>Une date écrite en dur ne marcherait pas : le filtre écarte ce qui précède le départ
     * comme ce qui dépasse l'heure serveur, et ces deux bornes bougent à chaque exécution.
     */
    Run startRun(Account owner, String visibility) throws Exception {
        MvcResult started = mvc.perform(post("/api/v1/activities")
                        .header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"RUN","title":"Sortie du matin","visibility":"%s"}
                                """.formatted(visibility)))
                .andExpect(status().isCreated()).andReturn();
        var body = json.readTree(started.getResponse().getContentAsString());
        return new Run(body.get("id").asText(), Instant.parse(body.get("startedAt").asText()));
    }

    /** Trois mètres par seconde vers l'est, à partir du départ de la course. */
    static String batch(Run run, int fromSequence, int toSequence) {
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

    MvcResult ingest(Account owner, Run run, String body, String key) throws Exception {
        var request = post("/api/v1/activities/" + run.id() + "/points")
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (key != null) {
            request = request.header("Idempotency-Key", key);
        }
        return mvc.perform(request).andReturn();
    }

    MvcResult ingest(Account owner, Run run, int fromSequence, int toSequence) throws Exception {
        return ingest(owner, run, batch(run, fromSequence, toSequence), null);
    }
}
