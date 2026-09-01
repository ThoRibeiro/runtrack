package com.runtrack.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Les « j'aime », les commentaires, et le fil qu'ils alimentent.
 *
 * <p>Le fil est une projection tenue par événements : il est donc en retard de quelques
 * millisecondes sur l'écriture, et les assertions attendent qu'il ait rattrapé. C'est le prix du
 * découplage, et le §10 l'accepte pour une vue de lecture.
 */
class EngagementFeedApiIT extends ApiIntegrationTest {

    private static final Duration PATIENCE = Duration.ofSeconds(15);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private CourseFixtures fixtures;

    @BeforeEach
    void setUpFixtures() {
        fixtures = new CourseFixtures(mvc, json);
    }

    private void follow(Account follower, Account followee) throws Exception {
        mvc.perform(post("/user/v1/" + followee.id() + "/follow")
                .header("Authorization", follower.bearer())).andExpect(status().isOk());
    }

    private JsonNode feedOf(Account reader) {
        try {
            MvcResult read = mvc.perform(get("/feed/v1").header("Authorization", reader.bearer()))
                    .andExpect(status().isOk()).andReturn();
            return json.readTree(read.getResponse().getContentAsString()).get("items");
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

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
        throw new AssertionError("Projection jamais rattrapée, dernier état : " + last);
    }

    private String postComment(Account author, Run run, String body, String parentId) throws Exception {
        String payload = parentId == null
                ? "{\"body\":\"%s\"}".formatted(body)
                : "{\"body\":\"%s\",\"parentId\":\"%s\"}".formatted(body, parentId);
        MvcResult posted = mvc.perform(post("/race/v1/" + run.id() + "/comments")
                        .header("Authorization", author.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(posted.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void likingAndUnlikingARun() throws Exception {
        Account marie = fixtures.newAccount();
        Account paul = fixtures.newAccount();
        Run run = fixtures.startRun(marie);

        mvc.perform(post("/race/v1/" + run.id() + "/likes")
                .header("Authorization", paul.bearer())).andExpect(status().isNoContent());
        mvc.perform(get("/race/v1/" + run.id() + "/likes")
                        .header("Authorization", paul.bearer()))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.likedByViewer").value(true));

        // Aimer deux fois est un clic renvoyé, pas un second « j'aime ».
        mvc.perform(post("/race/v1/" + run.id() + "/likes")
                .header("Authorization", paul.bearer())).andExpect(status().isNoContent());
        mvc.perform(get("/race/v1/" + run.id() + "/likes")
                        .header("Authorization", paul.bearer()))
                .andExpect(jsonPath("$.total").value(1));

        mvc.perform(delete("/race/v1/" + run.id() + "/likes")
                .header("Authorization", paul.bearer())).andExpect(status().isNoContent());
        mvc.perform(get("/race/v1/" + run.id() + "/likes")
                        .header("Authorization", paul.bearer()))
                .andExpect(jsonPath("$.total").value(0));
    }

    /** §5.5 : ce qu'on n'a pas le droit de voir répond « introuvable », et ne se like pas. */
    @Test
    void aPrivateRunCannotBeLikedByAnyoneElse() throws Exception {
        Account marie = fixtures.newAccount();
        Account paul = fixtures.newAccount();
        Run run = fixtures.startRun(marie, "PRIVATE");

        mvc.perform(post("/race/v1/" + run.id() + "/likes")
                        .header("Authorization", paul.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACTIVITY_NOT_FOUND"));
    }

    @Test
    void commentingReplyingEditingAndDeleting() throws Exception {
        Account marie = fixtures.newAccount();
        Account paul = fixtures.newAccount();
        Run run = fixtures.startRun(marie);

        String comment = postComment(paul, run, "Bravo", null);
        String reply = postComment(marie, run, "Merci", comment);

        mvc.perform(get("/race/v1/" + run.id() + "/comments")
                        .header("Authorization", paul.bearer()))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[1].parentId").value(comment));

        mvc.perform(patch("/comment/v1/" + comment)
                        .header("Authorization", paul.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Bravo !\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Bravo !"))
                .andExpect(jsonPath("$.editedAt").exists());

        mvc.perform(delete("/comment/v1/" + comment).header("Authorization", paul.bearer()))
                .andExpect(status().isNoContent());

        // La ligne reste — la réponse s'y accroche — mais son texte ne ressort plus.
        mvc.perform(get("/race/v1/" + run.id() + "/comments")
                        .header("Authorization", paul.bearer()))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].deleted").value(true))
                .andExpect(jsonPath("$.items[0].body").doesNotExist());
        assertThat(reply).isNotBlank();
    }

    @Test
    void nobodyEditsSomeoneElsesComment() throws Exception {
        Account marie = fixtures.newAccount();
        Account paul = fixtures.newAccount();
        Run run = fixtures.startRun(marie);
        String comment = postComment(paul, run, "Bravo", null);

        mvc.perform(patch("/comment/v1/" + comment)
                        .header("Authorization", marie.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Autre\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
    }

    @Test
    void aCommentLongerThanAThousandCharactersIsRefused() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = fixtures.startRun(marie);

        mvc.perform(post("/race/v1/" + run.id() + "/comments")
                        .header("Authorization", marie.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"%s\"}".formatted("a".repeat(1_001))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    /** La projection du fil, alimentée par événements : la course apparaît, puis ses compteurs. */
    @Test
    void theFeedShowsTheRunsOfThoseYouFollowWithTheirCounters() throws Exception {
        Account marie = fixtures.newAccount();
        Account paul = fixtures.newAccount();
        follow(paul, marie);
        Run run = fixtures.startRun(marie);

        awaitValue(() -> feedOf(paul), items -> !items.isEmpty());
        assertThat(feedOf(paul).get(0).get("activityId").asText()).isEqualTo(run.id());
        assertThat(feedOf(paul).get(0).get("author").get("displayName").asText()).isEqualTo("Coureur");

        mvc.perform(post("/race/v1/" + run.id() + "/likes")
                .header("Authorization", paul.bearer())).andExpect(status().isNoContent());
        postComment(paul, run, "Bravo", null);

        awaitValue(() -> feedOf(paul).get(0),
                item -> item.get("likeCount").asLong() == 1 && item.get("commentCount").asLong() == 1);
    }

    /** Une course repassée en privé disparaît du fil : c'est tout l'objet de l'événement ajouté. */
    @Test
    void aRunTurnedPrivateLeavesTheFeed() throws Exception {
        Account marie = fixtures.newAccount();
        Account paul = fixtures.newAccount();
        follow(paul, marie);
        Run run = fixtures.startRun(marie);
        awaitValue(() -> feedOf(paul), items -> !items.isEmpty());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/race/v1/" + run.id() + "/visibility")
                        .header("Authorization", marie.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PRIVATE\"}"))
                .andExpect(status().isOk());

        awaitValue(() -> feedOf(paul), JsonNode::isEmpty);
    }

    @Test
    void aDeletedRunLeavesTheFeed() throws Exception {
        Account marie = fixtures.newAccount();
        Account paul = fixtures.newAccount();
        follow(paul, marie);
        Run run = fixtures.startRun(marie);
        awaitValue(() -> feedOf(paul), items -> !items.isEmpty());

        mvc.perform(delete("/race/v1/" + run.id()).header("Authorization", marie.bearer()))
                .andExpect(status().isNoContent());

        awaitValue(() -> feedOf(paul), JsonNode::isEmpty);
    }

    @Test
    void theFeedNeedsAnAccount() throws Exception {
        mvc.perform(get("/feed/v1")).andExpect(status().isUnauthorized());
    }

    /** L'agrégation du §7 : plusieurs « j'aime » sur une course font une notification, pas dix. */
    @Test
    void severalLikesAggregateIntoOneNotification() throws Exception {
        Account marie = fixtures.newAccount();
        Account paul = fixtures.newAccount();
        Account lea = fixtures.newAccount();
        Run run = fixtures.startRun(marie);

        mvc.perform(post("/race/v1/" + run.id() + "/likes")
                .header("Authorization", paul.bearer())).andExpect(status().isNoContent());
        mvc.perform(post("/race/v1/" + run.id() + "/likes")
                .header("Authorization", lea.bearer())).andExpect(status().isNoContent());

        JsonNode aggregated = awaitValue(() -> {
            try {
                MvcResult listed = mvc.perform(get("/notification/v1")
                        .header("Authorization", marie.bearer())).andReturn();
                for (JsonNode item : json.readTree(listed.getResponse().getContentAsString())
                        .get("items")) {
                    if ("ACTIVITY_LIKED".equals(item.get("type").asText())) {
                        return item;
                    }
                }
                return null;
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }, found -> found != null && found.get("aggregateCount").asInt() == 2);

        assertThat(aggregated.get("aggregateCount").asInt()).isEqualTo(2);
    }
}
