package com.runtrack.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.runtrack.api.CourseFixtures.Account;
import com.runtrack.api.CourseFixtures.Run;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import com.runtrack.platform.events.EventPublications;
import com.runtrack.platform.events.EventPublicationsEndpoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * La chaîne complète du §7 : une course démarre, l'événement passe par le registre de
 * publications de Modulith, et une notification apparaît dans la boîte d'un abonné.
 *
 * <p>Aucun double ne peut prouver cela. Ce qui est vérifié ici, c'est que le traitement est bien
 * <b>asynchrone et après commit</b> — la réponse au coureur part avant que la notification existe
 * — et que la publication finit par être marquée complétée dans {@code event_publication}.
 */
class NotificationApiIT extends ApiIntegrationTest {

    private static final Duration PATIENCE = Duration.ofSeconds(15);

    /** Le flux SSE se lit sur une vraie connexion : voir {@link SseStream}. */
    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EventPublications publications;

    @Autowired
    private EventPublicationsEndpoint supervision;

    private CourseFixtures fixtures;

    @BeforeEach
    void setUpFixtures() {
        fixtures = new CourseFixtures(mvc, json);
    }

    /** Fait suivre {@code follower} → {@code followee} ; un compte public accepte sans demande. */
    private void follow(Account follower, Account followee) throws Exception {
        mvc.perform(post("/user/v1/" + followee.id() + "/follow")
                .header("Authorization", follower.bearer())).andExpect(status().isOk());
    }

    private JsonNode inboxOf(Account account) throws Exception {
        MvcResult listed = mvc.perform(get("/notification/v1")
                        .header("Authorization", account.bearer()))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(listed.getResponse().getContentAsString()).get("items");
    }

    private long unreadCountOf(Account account) throws Exception {
        MvcResult counted = mvc.perform(get("/notification/v1/unread-count")
                        .header("Authorization", account.bearer()))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(counted.getResponse().getContentAsString()).get("unread").asLong();
    }

    /**
     * Attend que le traitement asynchrone ait abouti.
     *
     * <p>Il n'y a rien à synchroniser : c'est tout l'intérêt du §7. Le coureur a déjà sa réponse,
     * et la notification arrive quand elle arrive.
     */
    private static <T> T awaitValue(Supplier<T> probe, java.util.function.Predicate<T> until) {
        Instant deadline = Instant.now().plus(PATIENCE);
        T last = null;
        while (Instant.now().isBefore(deadline)) {
            last = probe.get();
            if (until.test(last)) {
                return last;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("Traitement asynchrone jamais abouti, dernier état : " + last);
    }

    /**
     * Attend une notification d'une nature précise.
     *
     * <p>Attendre « au moins une non lue » ne suffirait pas : le seul fait de suivre quelqu'un en
     * produit déjà, et le test passerait sur la mauvaise. C'est la nature attendue qui fait foi.
     */
    private JsonNode awaitNotification(Account account, String type) {
        return awaitValue(() -> firstOfType(account, type), found -> found != null);
    }

    private JsonNode firstOfType(Account account, String type) {
        try {
            for (JsonNode notification : inboxOf(account)) {
                if (type.equals(notification.get("type").asText())) {
                    return notification;
                }
            }
            return null;
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    @Test
    void aStartedRunNotifiesTheFollowersAndPointsAtTheLiveTracking() throws Exception {
        Account marie = fixtures.newAccount();
        Account paul = fixtures.newAccount();
        follow(paul, marie);

        Run run = fixtures.startRun(marie);

        JsonNode started = awaitNotification(paul, "FRIEND_STARTED_ACTIVITY");
        assertThat(started.get("deepLink").asText()).isEqualTo("/activities/" + run.id() + "/live");
        assertThat(started.get("unread").asBoolean()).isTrue();
        assertThat(started.get("actorId").asText()).isEqualTo(marie.id());
    }

    /** Le coureur n'est pas notifié de sa propre course : il n'est pas son propre abonné. */
    @Test
    void theRunnerIsNotNotifiedOfTheirOwnRun() throws Exception {
        Account marie = fixtures.newAccount();
        Account paul = fixtures.newAccount();
        follow(paul, marie);

        fixtures.startRun(marie);
        awaitNotification(paul, "FRIEND_STARTED_ACTIVITY");

        // Marie a bien reçu quelque chose — l'abonnement de Paul — mais rien sur sa propre course.
        assertThat(firstOfType(marie, "FRIEND_STARTED_ACTIVITY")).isNull();
    }

    @Test
    void anEffectivelyPrivateRunNotifiesNobody() throws Exception {
        Account marie = fixtures.newAccount();
        Account paul = fixtures.newAccount();
        follow(paul, marie);

        fixtures.startRun(marie, "PRIVATE");
        // Laisser au traitement asynchrone le temps de ne rien faire : une assertion immédiate
        // passerait même si le fan-out notifiait tout le monde une seconde plus tard.
        Thread.sleep(1_500);

        assertThat(firstOfType(paul, "FRIEND_STARTED_ACTIVITY")).isNull();
    }

    @Test
    void finishingARunNotifiesTheFollowersToo() throws Exception {
        Account marie = fixtures.newAccount();
        Account paul = fixtures.newAccount();
        follow(paul, marie);
        Run run = fixtures.startRun(marie);
        awaitNotification(paul, "FRIEND_STARTED_ACTIVITY");

        mvc.perform(post("/race/v1/" + run.id() + "/finish")
                .header("Authorization", marie.bearer())).andExpect(status().isNoContent());

        assertThat(awaitNotification(paul, "FRIEND_FINISHED_ACTIVITY").get("deepLink").asText())
                .isEqualTo("/activities/" + run.id());
    }

    @Test
    void followingSomeoneTellsBothSides() throws Exception {
        Account marie = fixtures.newAccount();
        Account paul = fixtures.newAccount();

        follow(paul, marie);

        assertThat(awaitNotification(marie, "NEW_FOLLOWER").get("actorId").asText())
                .isEqualTo(paul.id());
        assertThat(awaitNotification(paul, "FOLLOW_ACCEPTED").get("actorId").asText())
                .isEqualTo(marie.id());
    }

    @Test
    void readingAndClearingTheInbox() throws Exception {
        Account marie = fixtures.newAccount();
        Account paul = fixtures.newAccount();
        follow(paul, marie);
        fixtures.startRun(marie);
        String id = awaitNotification(paul, "FRIEND_STARTED_ACTIVITY").get("id").asText();
        long unread = unreadCountOf(paul);

        mvc.perform(post("/notification/v1/" + id + "/read")
                .header("Authorization", paul.bearer())).andExpect(status().isNoContent());
        assertThat(unreadCountOf(paul)).isEqualTo(unread - 1);

        mvc.perform(post("/notification/v1/read-all").header("Authorization", paul.bearer()))
                .andExpect(status().isOk());
        assertThat(unreadCountOf(paul)).isZero();

        // Une boîte déjà vide ne compte rien de plus : l'opération est idempotente.
        mvc.perform(post("/notification/v1/read-all").header("Authorization", paul.bearer()))
                .andExpect(jsonPath("$.marked").value(0));
    }

    @Test
    void anotherAccountsNotificationCannotBeMarkedRead() throws Exception {
        Account marie = fixtures.newAccount();
        Account paul = fixtures.newAccount();
        Account lea = fixtures.newAccount();
        follow(paul, marie);
        fixtures.startRun(marie);
        String id = awaitNotification(paul, "FRIEND_STARTED_ACTIVITY").get("id").asText();

        mvc.perform(post("/notification/v1/" + id + "/read")
                        .header("Authorization", lea.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
    }

    @Test
    void aMutedNatureNeverReachesTheInbox() throws Exception {
        Account marie = fixtures.newAccount();
        Account paul = fixtures.newAccount();
        follow(paul, marie);
        awaitNotification(paul, "FOLLOW_ACCEPTED");
        mvc.perform(post("/notification/v1/read-all").header("Authorization", paul.bearer()))
                .andExpect(status().isOk());

        mvc.perform(patch("/user/v1/me/notification-preferences")
                        .header("Authorization", paul.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"muted\":[\"FRIEND_STARTED_ACTIVITY\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.muted[0]").value("FRIEND_STARTED_ACTIVITY"));

        fixtures.startRun(marie);
        Thread.sleep(1_500);

        assertThat(unreadCountOf(paul)).isZero();
    }

    /** L'écran de réglages ne tient pas sa propre liste : le serveur énumère ce qui existe. */
    @Test
    void preferencesAnnounceEveryAvailableNature() throws Exception {
        Account marie = fixtures.newAccount();

        mvc.perform(get("/user/v1/me/notification-preferences")
                        .header("Authorization", marie.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.muted").isEmpty())
                .andExpect(jsonPath("$.available[0]").value("FRIEND_STARTED_ACTIVITY"));
    }

    @Test
    void anUnknownNatureIsARequestError() throws Exception {
        Account marie = fixtures.newAccount();

        mvc.perform(patch("/user/v1/me/notification-preferences")
                        .header("Authorization", marie.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"muted\":[\"PIGEON_ARRIVED\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_VALUE"));
    }

    @Test
    void anInboxNeedsAnAccount() throws Exception {
        mvc.perform(get("/notification/v1")).andExpect(status().isUnauthorized());
    }

    /**
     * La preuve que l'outbox est bien celle de Modulith : chaque événement traité laisse une ligne
     * complétée dans {@code event_publication}. Si le registre n'était pas branché, le fan-out
     * marcherait quand même — et se perdrait au premier redémarrage.
     */
    @Test
    void everyHandledEventIsRecordedAndCompletedInTheRegistry() throws Exception {
        Account marie = fixtures.newAccount();
        Account paul = fixtures.newAccount();
        follow(paul, marie);
        fixtures.startRun(marie);
        awaitNotification(paul, "FRIEND_STARTED_ACTIVITY");

        Long completed = awaitValue(() -> jdbc.queryForObject("""
                SELECT count(*) FROM event_publication
                WHERE completion_date IS NOT NULL AND event_type LIKE '%ActivityStarted'
                """, Long.class), count -> count != null && count > 0);

        assertThat(completed).isPositive();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM event_publication WHERE completion_date IS NULL", Long.class))
                .isZero();
    }

    /**
     * Le flux SSE de la boîte de réception : la pastille s'allume sans recharger la page.
     *
     * <p>Même canal que le suivi d'une course, un sujet par destinataire. L'instantané est la
     * première page de non-lues, de sorte que le panneau se peint sans second appel.
     */
    @Test
    void aNotificationReachesTheSpectatorLive() throws Exception {
        Account marie = fixtures.newAccount();
        Account paul = fixtures.newAccount();
        follow(paul, marie);
        awaitNotification(paul, "FOLLOW_ACCEPTED");

        SseStream stream = SseStream.open(port, "/notification/v1/stream", paul.bearer(), null);

        // L'instantané : ce que Paul n'a pas encore lu, dès la connexion.
        String snapshot = stream.await(content -> content.contains("FOLLOW_ACCEPTED"));
        assertThat(snapshot).contains("event:notification");

        fixtures.startRun(marie);

        assertThat(stream.await(content -> content.contains("FRIEND_STARTED_ACTIVITY")))
                .contains("event:notification");
        stream.close();
    }

    /**
     * La supervision du §7 : ce que l'endpoint Actuator rend doit décrire le registre réel.
     *
     * <p>Une fois tout traité, il ne reste rien en souffrance et aucune lettre morte — ce qui est
     * précisément l'état qu'on veut pouvoir constater sans ouvrir la base.
     */
    @Test
    void theActuatorEndpointReportsAHealthyOutbox() throws Exception {
        Account marie = fixtures.newAccount();
        Account paul = fixtures.newAccount();
        follow(paul, marie);
        fixtures.startRun(marie);
        awaitNotification(paul, "FRIEND_STARTED_ACTIVITY");

        awaitValue(publications::incompleteCount, count -> count == 0L);

        assertThat(supervision.publications())
                .containsEntry("incomplete", 0L)
                .containsEntry("deadLettered", 0L)
                .containsKey("maxAttempts")
                // Plus rien en souffrance : il n'y a donc pas de « plus vieille » à dater.
                .doesNotContainKey("oldestIncompleteSeconds");
    }
}
