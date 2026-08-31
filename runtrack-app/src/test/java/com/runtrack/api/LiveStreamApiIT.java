package com.runtrack.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.runtrack.api.CourseFixtures.Account;
import com.runtrack.api.CourseFixtures.Run;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

/**
 * Le suivi en direct de bout en bout : Dragonfly transporte, le registre distribue, le client
 * reçoit du SSE.
 *
 * <p>C'est le seul test où la chaîne complète existe. Les doubles en mémoire prouvent l'ordre
 * des opérations ; ils ne prouvent ni qu'une entrée écrite dans un Stream ressort du côté du
 * lecteur, ni qu'un {@code Last-Event-ID} retrouve sa place.
 */
class LiveStreamApiIT extends ApiIntegrationTest {

    private static final Duration PATIENCE = Duration.ofSeconds(10);
    private static final Pattern EVENT_ID = Pattern.compile("id:(\\S+)");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private CourseFixtures fixtures;

    @BeforeEach
    void setUpFixtures() {
        fixtures = new CourseFixtures(mvc, json);
    }

    private MvcResult openStream(Account viewer, Run run, String lastEventId) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/activities/" + run.id() + "/stream")
                .accept(MediaType.TEXT_EVENT_STREAM);
        if (viewer != null) {
            request = request.header("Authorization", viewer.bearer());
        }
        if (lastEventId != null) {
            request = request.header("Last-Event-ID", lastEventId);
        }
        return mvc.perform(request).andReturn();
    }

    /**
     * Attend que le flux contienne ce qu'on cherche.
     *
     * <p>Une attente fixe serait soit trop courte — le relais passe par Dragonfly et son
     * {@code XREAD} — soit inutilement lente. On interroge le tampon de réponse, qui se remplit
     * au fur et à mesure des écritures de l'émetteur.
     */
    private static String awaitStream(MvcResult stream, Predicate<String> until) throws Exception {
        Instant deadline = Instant.now().plus(PATIENCE);
        String content = "";
        while (Instant.now().isBefore(deadline)) {
            content = stream.getResponse().getContentAsString();
            if (until.test(content)) {
                return content;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Flux incomplet après " + PATIENCE + " :\n" + content);
    }

    /** Referme la connexion : sans cela, chaque test laisserait un spectateur derrière lui. */
    private static void close(MvcResult stream) {
        stream.getRequest().getAsyncContext().complete();
    }

    private static int countOf(String stream, String event) {
        return stream.split("event:" + event, -1).length - 1;
    }

    private static String lastEventIdOf(String stream) {
        Matcher matcher = EVENT_ID.matcher(stream);
        String last = null;
        while (matcher.find()) {
            last = matcher.group(1);
        }
        return last;
    }

    @Test
    void aSpectatorArrivingMidRunGetsTheStateTheStatsAndTheTrack() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = fixtures.startRun(marie);
        fixtures.ingest(marie, run, 1, 3);

        MvcResult stream = openStream(marie, run, null);
        String received = awaitStream(stream, content -> countOf(content, "position") >= 3);

        assertThat(received).contains("event:status").contains("event:stats");
        assertThat(received).contains("\"status\":\"Live\"");
        assertThat(countOf(received, "position")).isEqualTo(3);
        // L'instantané n'est pas une entrée du journal : rien à reprendre depuis là.
        assertThat(lastEventIdOf(received)).isNull();
        close(stream);
    }

    @Test
    void pointsIngestedAfterwardsArriveLiveWithTheirStreamIdentifier() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = fixtures.startRun(marie);

        MvcResult stream = openStream(marie, run, null);
        awaitStream(stream, content -> content.contains("event:stats"));

        fixtures.ingest(marie, run, 1, 3);
        String received = awaitStream(stream, content -> countOf(content, "position") >= 3);

        assertThat(received).contains("\"sequenceNumber\":3");
        assertThat(lastEventIdOf(received)).isNotNull();
        close(stream);
    }

    @Test
    void aPauseReachesTheSpectator() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = fixtures.startRun(marie);

        MvcResult stream = openStream(marie, run, null);
        awaitStream(stream, content -> content.contains("event:stats"));

        mvc.perform(post("/api/v1/activities/" + run.id() + "/pause")
                .header("Authorization", marie.bearer())).andExpect(status().isNoContent());

        assertThat(awaitStream(stream, content -> content.contains("\"status\":\"Paused\"")))
                .contains("event:status");
        close(stream);
    }

    /** Sans battement, un proxy referme une connexion restée silencieuse. */
    @Test
    void theHeartbeatKeepsASilentConnectionAlive() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = fixtures.startRun(marie);

        MvcResult stream = openStream(marie, run, null);

        assertThat(awaitStream(stream, content -> content.contains("event:heartbeat")))
                .contains("event:heartbeat");
        close(stream);
    }

    /**
     * La reprise du §4 : le client revient avec son dernier identifiant et reçoit ce qu'il a
     * manqué — pas un instantané qui redessinerait tout, pas un trou non plus.
     */
    @Test
    void reconnectingWithLastEventIdReplaysOnlyWhatWasMissed() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = fixtures.startRun(marie);

        MvcResult first = openStream(marie, run, null);
        awaitStream(first, content -> content.contains("event:stats"));
        fixtures.ingest(marie, run, 1, 3);
        String seen = awaitStream(first, content -> countOf(content, "position") >= 3);
        String lastEventId = lastEventIdOf(seen);
        close(first);

        fixtures.ingest(marie, run, 4, 6);

        MvcResult resumed = openStream(marie, run, lastEventId);
        String replayed = awaitStream(resumed, content -> content.contains("\"sequenceNumber\":6"));

        // Ni instantané ni redite : seulement la suite.
        assertThat(replayed).doesNotContain("\"sequenceNumber\":1");
        assertThat(replayed).contains("\"sequenceNumber\":4");
        close(resumed);
    }

    /** Un identifiant que le journal ne connaît plus : on repart d'un instantané, pas d'un trou. */
    @Test
    void anUnusableLastEventIdFallsBackToTheSnapshot() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = fixtures.startRun(marie);
        fixtures.ingest(marie, run, 1, 3);

        MvcResult stream = openStream(marie, run, "1-0");
        String received = awaitStream(stream, content -> countOf(content, "position") >= 3);

        assertThat(received).contains("event:status").contains("event:stats");
        close(stream);
    }

    @Test
    void aFinishedRunSendsItsFinalStateAndHangsUp() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = fixtures.startRun(marie);
        fixtures.ingest(marie, run, 1, 3);
        mvc.perform(post("/api/v1/activities/" + run.id() + "/finish")
                .header("Authorization", marie.bearer())).andExpect(status().isNoContent());

        MvcResult stream = openStream(marie, run, null);
        String received = awaitStream(stream, content -> content.contains("event:stats"));

        assertThat(received).contains("\"status\":\"Finished\"");
        assertThat(countOf(received, "position")).isEqualTo(3);

        // Plus rien ne viendra : la connexion est refermée, et le battement de cœur — qui bat
        // toutes les 300 ms en test — ne l'atteint plus.
        Thread.sleep(1_000);
        assertThat(stream.getResponse().getContentAsString()).doesNotContain("event:heartbeat");
    }

    @Test
    void aPublicRunIsWatchableWithoutAnAccount() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = fixtures.startRun(marie);

        MvcResult stream = openStream(null, run, null);

        assertThat(awaitStream(stream, content -> content.contains("event:stats")))
                .contains("event:status");
        close(stream);
    }

    /** Le direct passe par la même politique d'accès : une course privée reste introuvable. */
    @Test
    void aPrivateRunIsNotStreamedToAnyoneElse() throws Exception {
        Account marie = fixtures.newAccount();
        Account paul = fixtures.newAccount();
        Run run = fixtures.startRun(marie, "PRIVATE");

        mvc.perform(get("/api/v1/activities/" + run.id() + "/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .header("Authorization", paul.bearer()))
                .andExpect(status().isNotFound());
    }
}
