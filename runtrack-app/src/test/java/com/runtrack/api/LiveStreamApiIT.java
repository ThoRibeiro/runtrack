package com.runtrack.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.runtrack.api.CourseFixtures.Account;
import com.runtrack.api.CourseFixtures.Run;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
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

    private static final Pattern EVENT_ID = Pattern.compile("id:(\\S+)");

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private CourseFixtures fixtures;

    @BeforeEach
    void setUpFixtures() {
        fixtures = new CourseFixtures(mvc, json);
    }

    private SseStream openStream(Account viewer, Run run, String lastEventId) throws Exception {
        return SseStream.open(port, "/race/v1/" + run.id() + "/stream",
                viewer == null ? null : viewer.bearer(), lastEventId);
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

        SseStream stream = openStream(marie, run, null);
        String received = stream.await(content -> countOf(content, "position") >= 3);

        assertThat(received).contains("event:status").contains("event:stats");
        assertThat(received).contains("\"status\":\"Live\"");
        assertThat(countOf(received, "position")).isEqualTo(3);
        // L'instantané n'est pas une entrée du journal : rien à reprendre depuis là.
        assertThat(lastEventIdOf(received)).isNull();
        stream.close();
    }

    @Test
    void pointsIngestedAfterwardsArriveLiveWithTheirStreamIdentifier() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = fixtures.startRun(marie);

        SseStream stream = openStream(marie, run, null);
        stream.await(content -> content.contains("event:stats"));

        fixtures.ingest(marie, run, 1, 3);
        String received = stream.await(content -> countOf(content, "position") >= 3);

        assertThat(received).contains("\"sequenceNumber\":3");
        assertThat(lastEventIdOf(received)).isNotNull();
        stream.close();
    }

    @Test
    void aPauseReachesTheSpectator() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = fixtures.startRun(marie);

        SseStream stream = openStream(marie, run, null);
        stream.await(content -> content.contains("event:stats"));

        mvc.perform(post("/race/v1/" + run.id() + "/pause")
                .header("Authorization", marie.bearer())).andExpect(status().isNoContent());

        assertThat(stream.await(content -> content.contains("\"status\":\"Paused\"")))
                .contains("event:status");
        stream.close();
    }

    /** Sans battement, un proxy referme une connexion restée silencieuse. */
    @Test
    void theHeartbeatKeepsASilentConnectionAlive() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = fixtures.startRun(marie);

        SseStream stream = openStream(marie, run, null);

        assertThat(stream.await(content -> content.contains("event:heartbeat")))
                .contains("event:heartbeat");
        stream.close();
    }

    /**
     * La reprise du §4 : le client revient avec son dernier identifiant et reçoit ce qu'il a
     * manqué — pas un instantané qui redessinerait tout, pas un trou non plus.
     */
    @Test
    void reconnectingWithLastEventIdReplaysOnlyWhatWasMissed() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = fixtures.startRun(marie);

        SseStream first = openStream(marie, run, null);
        first.await(content -> content.contains("event:stats"));
        fixtures.ingest(marie, run, 1, 3);
        String seen = first.await(content -> countOf(content, "position") >= 3);
        String lastEventId = lastEventIdOf(seen);
        first.close();

        fixtures.ingest(marie, run, 4, 6);

        SseStream resumed = openStream(marie, run, lastEventId);
        String replayed = resumed.await(content -> content.contains("\"sequenceNumber\":6"));

        // Ni instantané ni redite : seulement la suite.
        assertThat(replayed).doesNotContain("\"sequenceNumber\":1");
        assertThat(replayed).contains("\"sequenceNumber\":4");
        resumed.close();
    }

    /** Un identifiant que le journal ne connaît plus : on repart d'un instantané, pas d'un trou. */
    @Test
    void anUnusableLastEventIdFallsBackToTheSnapshot() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = fixtures.startRun(marie);
        fixtures.ingest(marie, run, 1, 3);

        SseStream stream = openStream(marie, run, "1-0");
        String received = stream.await(content -> countOf(content, "position") >= 3);

        assertThat(received).contains("event:status").contains("event:stats");
        stream.close();
    }

    @Test
    void aFinishedRunSendsItsFinalStateAndHangsUp() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = fixtures.startRun(marie);
        fixtures.ingest(marie, run, 1, 3);
        mvc.perform(post("/race/v1/" + run.id() + "/finish")
                .header("Authorization", marie.bearer())).andExpect(status().isNoContent());

        SseStream stream = openStream(marie, run, null);
        String received = stream.await(content -> content.contains("event:stats"));

        assertThat(received).contains("\"status\":\"Finished\"");
        assertThat(countOf(received, "position")).isEqualTo(3);

        // Plus rien ne viendra : le serveur raccroche de lui-même, et le battement de cœur —
        // qui bat toutes les 300 ms en test — ne l'atteint plus.
        assertThat(stream.awaitEnd()).doesNotContain("event:heartbeat");
    }

    @Test
    void aPublicRunIsWatchableWithoutAnAccount() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = fixtures.startRun(marie);

        SseStream stream = openStream(null, run, null);

        assertThat(stream.await(content -> content.contains("event:stats")))
                .contains("event:status");
        stream.close();
    }

    /** Le direct passe par la même politique d'accès : une course privée reste introuvable. */
    @Test
    void aPrivateRunIsNotStreamedToAnyoneElse() throws Exception {
        Account marie = fixtures.newAccount();
        Account paul = fixtures.newAccount();
        Run run = fixtures.startRun(marie, "PRIVATE");

        mvc.perform(get("/race/v1/" + run.id() + "/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .header("Authorization", paul.bearer()))
                .andExpect(status().isNotFound());
    }
}
