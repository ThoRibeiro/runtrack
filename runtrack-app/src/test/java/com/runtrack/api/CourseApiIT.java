package com.runtrack.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/** Le cycle de vie d'une course vu du client, contre une vraie base. */
class CourseApiIT extends ApiIntegrationTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private record Account(String id, String token) {
    }

    private Account newAccount() throws Exception {
        String handle = "c" + COUNTER.incrementAndGet() + System.nanoTime() % 100_000;
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

    private String startRun(Account owner, String visibility) throws Exception {
        MvcResult started = mvc.perform(post("/api/v1/activities")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"RUN","title":"Sortie du matin","visibility":"%s"}
                                """.formatted(visibility)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("Live"))
                .andReturn();
        return json.readTree(started.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void startPauseResumeFinishFollowsTheLifecycle() throws Exception {
        Account marie = newAccount();
        String runId = startRun(marie, "PUBLIC");

        mvc.perform(post("/api/v1/activities/" + runId + "/pause").header("Authorization", bearer(marie)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/activities/" + runId).header("Authorization", bearer(marie)))
                .andExpect(jsonPath("$.status").value("Paused"));

        mvc.perform(post("/api/v1/activities/" + runId + "/resume").header("Authorization", bearer(marie)))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/activities/" + runId + "/finish").header("Authorization", bearer(marie)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/activities/" + runId).header("Authorization", bearer(marie)))
                .andExpect(jsonPath("$.status").value("Finished"))
                .andExpect(jsonPath("$.endedAt").exists())
                .andExpect(jsonPath("$.stats.distanceMeters").value(0.0));
    }

    @Test
    void anIllegalTransitionIsAConflict() throws Exception {
        Account marie = newAccount();
        String runId = startRun(marie, "PUBLIC");

        mvc.perform(post("/api/v1/activities/" + runId + "/resume").header("Authorization", bearer(marie)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVITY_NOT_PAUSED"));
    }

    @Test
    void aFinishedRunRefusesFurtherTransitions() throws Exception {
        Account marie = newAccount();
        String runId = startRun(marie, "PUBLIC");
        mvc.perform(post("/api/v1/activities/" + runId + "/finish").header("Authorization", bearer(marie)));

        mvc.perform(post("/api/v1/activities/" + runId + "/finish").header("Authorization", bearer(marie)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVITY_ALREADY_ENDED"));
    }

    @Test
    void aPublicRunIsReadableWithoutAuthentication() throws Exception {
        Account marie = newAccount();
        String runId = startRun(marie, "PUBLIC");

        mvc.perform(get("/api/v1/activities/" + runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Sortie du matin"));
    }

    /** Une course privée se comporte comme si elle n'existait pas. */
    @Test
    void aPrivateRunIsInvisibleToOthers() throws Exception {
        Account marie = newAccount();
        Account paul = newAccount();
        String runId = startRun(marie, "PRIVATE");

        mvc.perform(get("/api/v1/activities/" + runId).header("Authorization", bearer(paul)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACTIVITY_NOT_FOUND"));
        mvc.perform(get("/api/v1/activities/" + runId))
                .andExpect(status().isNotFound());
    }

    /** Un compte suivi et accepté voit les courses réservées aux abonnés. */
    @Test
    void aFollowersOnlyRunOpensToAnAcceptedFollower() throws Exception {
        Account marie = newAccount();
        Account paul = newAccount();
        String runId = startRun(marie, "FOLLOWERS");

        mvc.perform(get("/api/v1/activities/" + runId).header("Authorization", bearer(paul)))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/users/" + marie.id() + "/follow").header("Authorization", bearer(paul)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/activities/" + runId).header("Authorization", bearer(paul)))
                .andExpect(status().isOk());
    }

    /** Bloquer referme immédiatement l'accès, y compris à une course publique. */
    @Test
    void blockingClosesAccessToAPublicRun() throws Exception {
        Account marie = newAccount();
        Account paul = newAccount();
        String runId = startRun(marie, "PUBLIC");
        mvc.perform(get("/api/v1/activities/" + runId).header("Authorization", bearer(paul)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/users/" + paul.id() + "/block").header("Authorization", bearer(marie)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/activities/" + runId).header("Authorization", bearer(paul)))
                .andExpect(status().isNotFound());
    }

    @Test
    void actingOnSomeoneElsesRunLooksLikeItDoesNotExist() throws Exception {
        Account marie = newAccount();
        Account paul = newAccount();
        String runId = startRun(marie, "PUBLIC");

        mvc.perform(post("/api/v1/activities/" + runId + "/pause").header("Authorization", bearer(paul)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/activities/" + runId).header("Authorization", bearer(paul)))
                .andExpect(status().isNotFound());
    }

    @Test
    void startingNeedsAuthentication() throws Exception {
        mvc.perform(post("/api/v1/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"RUN\",\"title\":\"Sortie\",\"visibility\":\"PUBLIC\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anUnknownActivityTypeIsUnprocessable() throws Exception {
        Account marie = newAccount();

        mvc.perform(post("/api/v1/activities")
                        .header("Authorization", bearer(marie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"TELEPORT\",\"title\":\"Sortie\",\"visibility\":\"PUBLIC\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_VALUE"));
    }

    @Test
    void anEmptyTitleIsUnprocessable() throws Exception {
        Account marie = newAccount();

        mvc.perform(post("/api/v1/activities")
                        .header("Authorization", bearer(marie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"RUN\",\"title\":\"  \",\"visibility\":\"PUBLIC\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void renamingAndChangingVisibilityAreOwnerOnly() throws Exception {
        Account marie = newAccount();
        Account paul = newAccount();
        String runId = startRun(marie, "PUBLIC");

        mvc.perform(patch("/api/v1/activities/" + runId)
                        .header("Authorization", bearer(marie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Fractionné\",\"description\":\"30/30\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Fractionné"));

        mvc.perform(put("/api/v1/activities/" + runId + "/visibility")
                        .header("Authorization", bearer(marie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PRIVATE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("PRIVATE"));

        mvc.perform(patch("/api/v1/activities/" + runId)
                        .header("Authorization", bearer(paul))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Pirate\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listingAProfileHidesWhatTheReaderCannotSee() throws Exception {
        Account marie = newAccount();
        Account paul = newAccount();
        startRun(marie, "PUBLIC");
        startRun(marie, "PRIVATE");

        mvc.perform(get("/api/v1/users/" + marie.id() + "/activities").header("Authorization", bearer(marie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));

        mvc.perform(get("/api/v1/users/" + marie.id() + "/activities").header("Authorization", bearer(paul)))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void theLiveScreenShowsRunningActivitiesOfFollowedAccounts() throws Exception {
        Account marie = newAccount();
        Account paul = newAccount();
        startRun(marie, "PUBLIC");
        mvc.perform(post("/api/v1/users/" + marie.id() + "/follow").header("Authorization", bearer(paul)));

        mvc.perform(get("/api/v1/activities/live").header("Authorization", bearer(paul)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].status").value("Live"));
    }

    @Test
    void deletingRemovesTheRun() throws Exception {
        Account marie = newAccount();
        String runId = startRun(marie, "PUBLIC");

        mvc.perform(delete("/api/v1/activities/" + runId).header("Authorization", bearer(marie)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/activities/" + runId).header("Authorization", bearer(marie)))
                .andExpect(status().isNotFound());
    }
}
