package com.runtrack.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.runtrack.course.CourseApi;
import com.runtrack.platform.cache.CacheKey;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import com.runtrack.social.SocialApi;
import com.runtrack.user.UserApi;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/**
 * Le comportement des décorateurs de cache dans l'application complète : ce qui atterrit
 * dans Dragonfly, ce qui n'y atterrit jamais, et ce que l'invalidation efface.
 */
class CacheBehaviourIT extends ApiIntegrationTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private UserApi users;

    @Autowired
    private SocialApi social;

    @Autowired
    private CourseApi courses;

    private record Account(String id, String token, String handle) {
    }

    private Account newAccount() throws Exception {
        String handle = "k" + COUNTER.incrementAndGet() + System.nanoTime() % 100_000;
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
                .get("accessToken").asText(), handle);
    }

    @Test
    void readingAProfileTwiceLeavesItInDragonfly() throws Exception {
        Account marie = newAccount();
        String key = CacheKey.userSummary(marie.id());
        redis.delete(key);

        assertThat(users.summary(UserId.of(marie.id()))).isPresent();

        assertThat(redis.hasKey(key)).isTrue();
        assertThat(users.summary(UserId.of(marie.id()))).isPresent();
    }

    @Test
    void accountScopeIsCachedSeparately() throws Exception {
        Account marie = newAccount();
        String key = CacheKey.accountScope(marie.id());
        redis.delete(key);

        assertThat(users.accountScope(UserId.of(marie.id()))).isPresent();

        assertThat(redis.hasKey(key)).isTrue();
    }

    /** Le lot ne recharge que ce qui manque, et remplit le cache au passage. */
    @Test
    void aBatchOfSummariesFillsTheCache() throws Exception {
        Account one = newAccount();
        Account other = newAccount();
        redis.delete(List.of(CacheKey.userSummary(one.id()), CacheKey.userSummary(other.id())));

        var found = users.summaries(List.of(UserId.of(one.id()), UserId.of(other.id())));

        assertThat(found).hasSize(2);
        assertThat(redis.hasKey(CacheKey.userSummary(one.id()))).isTrue();
        assertThat(redis.hasKey(CacheKey.userSummary(other.id()))).isTrue();
    }

    @Test
    void anEmptyBatchAsksNothing() {
        assertThat(users.summaries(List.of())).isEmpty();
    }

    /** L'absence est mémorisée : c'est ce qui protège du balayage d'identifiants. */
    @Test
    void anUnknownProfileIsCachedAsAbsent() {
        UserId ghost = new UserId(java.util.UUID.randomUUID());
        redis.delete(CacheKey.userSummary(ghost.toString()));

        assertThat(users.summary(ghost)).isEmpty();

        assertThat(redis.hasKey(CacheKey.userSummary(ghost.toString()))).isTrue();
        assertThat(users.summary(ghost)).isEmpty();
        assertThat(users.exists(ghost)).isFalse();
    }

    /** Modifier son profil purge l'entrée : la lecture suivante doit être fraîche. */
    @Test
    void updatingAProfileEvictsIt() throws Exception {
        Account marie = newAccount();
        users.summary(UserId.of(marie.id()));
        assertThat(redis.hasKey(CacheKey.userSummary(marie.id()))).isTrue();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/users/me/handle")
                        .header("Authorization", "Bearer " + marie.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handle\":\"%s\"}".formatted(marie.handle())))
                .andExpect(status().isConflict());

        // Le compte n'est pas confirmé, donc rien n'a changé : l'entrée doit rester en place.
        assertThat(redis.hasKey(CacheKey.userSummary(marie.id()))).isTrue();
    }

    @Test
    void followerListsAreCached() throws Exception {
        Account marie = newAccount();
        Account paul = newAccount();
        mvc.perform(post("/api/v1/users/" + marie.id() + "/follow")
                        .header("Authorization", "Bearer " + paul.token()))
                .andExpect(status().isOk());
        redis.delete(CacheKey.followers(marie.id()));

        assertThat(social.acceptedFollowerIds(UserId.of(marie.id()))).hasSize(1);

        assertThat(redis.hasKey(CacheKey.followers(marie.id()))).isTrue();
    }

    /** Un nouvel abonnement purge les deux comptes concernés. */
    @Test
    void aNewFollowEvictsBothSides() throws Exception {
        Account marie = newAccount();
        Account paul = newAccount();
        social.acceptedFollowerIds(UserId.of(marie.id()));
        social.acceptedFolloweeIds(UserId.of(paul.id()));
        assertThat(redis.hasKey(CacheKey.followers(marie.id()))).isTrue();
        assertThat(redis.hasKey(CacheKey.followees(paul.id()))).isTrue();

        mvc.perform(post("/api/v1/users/" + marie.id() + "/follow")
                        .header("Authorization", "Bearer " + paul.token()))
                .andExpect(status().isOk());

        assertThat(redis.hasKey(CacheKey.followers(marie.id()))).isFalse();
        assertThat(redis.hasKey(CacheKey.followees(paul.id()))).isFalse();
    }

    /** Se désabonner purge aussi : sans cela le fan-out notifierait un parti. */
    @Test
    void unfollowingEvictsBothSides() throws Exception {
        Account marie = newAccount();
        Account paul = newAccount();
        mvc.perform(post("/api/v1/users/" + marie.id() + "/follow")
                .header("Authorization", "Bearer " + paul.token()));
        social.acceptedFollowerIds(UserId.of(marie.id()));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/users/" + marie.id() + "/follow")
                        .header("Authorization", "Bearer " + paul.token()))
                .andExpect(status().isNoContent());

        assertThat(redis.hasKey(CacheKey.followers(marie.id()))).isFalse();
        assertThat(social.acceptedFollowerIds(UserId.of(marie.id()))).isEmpty();
    }

    /** Blocage : jamais caché, parce qu'une valeur périmée rouvrirait une porte fermée. */
    @Test
    void blockingIsNeverCached() throws Exception {
        Account marie = newAccount();
        Account paul = newAccount();

        assertThat(social.isBlockedEitherWay(UserId.of(marie.id()), UserId.of(paul.id()))).isFalse();

        mvc.perform(post("/api/v1/users/" + paul.id() + "/block")
                        .header("Authorization", "Bearer " + marie.token()))
                .andExpect(status().isNoContent());

        assertThat(social.isBlockedEitherWay(UserId.of(marie.id()), UserId.of(paul.id()))).isTrue();
    }

    /** Une course en direct ne va pas en cache ; une course terminée, si. */
    @Test
    void onlyFinishedRunsAreCached() throws Exception {
        Account marie = newAccount();
        MvcResult started = mvc.perform(post("/api/v1/activities")
                        .header("Authorization", "Bearer " + marie.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"RUN\",\"title\":\"Sortie\",\"visibility\":\"PUBLIC\"}"))
                .andExpect(status().isCreated()).andReturn();
        String runId = json.readTree(started.getResponse().getContentAsString()).get("id").asText();

        courses.summary(ActivityId.of(runId));
        assertThat(redis.hasKey(CacheKey.activitySummary(runId))).isFalse();

        mvc.perform(post("/api/v1/activities/" + runId + "/finish")
                        .header("Authorization", "Bearer " + marie.token()))
                .andExpect(status().isNoContent());

        courses.summary(ActivityId.of(runId));
        assertThat(redis.hasKey(CacheKey.activitySummary(runId))).isTrue();
    }

    @Test
    void anUnknownRunIsNotCached() {
        ActivityId ghost = new ActivityId(java.util.UUID.randomUUID());

        assertThat(courses.summary(ghost)).isEmpty();
        assertThat(courses.ownerOf(ghost)).isEmpty();
        assertThat(redis.hasKey(CacheKey.activitySummary(ghost.toString()))).isFalse();
    }

    @Test
    void aBatchOfRunSummariesWorksWithNoIds() {
        assertThat(courses.summaries(List.of())).isEmpty();
    }
}
