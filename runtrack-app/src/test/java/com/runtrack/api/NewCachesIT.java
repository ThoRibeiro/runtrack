package com.runtrack.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.runtrack.api.CourseFixtures.Account;
import com.runtrack.api.CourseFixtures.Run;
import com.runtrack.platform.cache.CacheKey;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.runtrack.platform.cache.CacheGateway;
import tools.jackson.databind.ObjectMapper;

/**
 * Les trois caches du §6 qui manquaient, et le verrou anti-stampede.
 *
 * <p>Chacun est vérifié sur les trois points que le §6 exige : la valeur atterrit bien dans
 * Dragonfly, une invalidation la chasse, et l'application répond juste dans les deux cas.
 */
class NewCachesIT extends ApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private CacheGateway cache;

    private CourseFixtures fixtures;

    @BeforeEach
    void setUpFixtures() {
        fixtures = new CourseFixtures(mvc, json);
    }

    private String issueLinkFor(Account owner, Run run) throws Exception {
        MvcResult created = mvc.perform(post("/race/v1/" + run.id() + "/share-links")
                        .header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(created.getResponse().getContentAsString()).get("token").asText();
    }

    private static String hashOf(String token) {
        return new com.runtrack.sharing.usecases.model.link.ShareToken(token).hash();
    }

    /** Le jeton de partage : la lecture la plus chaude du module, sur un chemin public. */
    @Test
    void resolvingAShareTokenTwiceLeavesItInDragonfly() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = fixtures.startRun(marie, "PRIVATE");
        String token = issueLinkFor(marie, run);
        String key = CacheKey.shareToken(hashOf(token));

        assertThat(redis.hasKey(key)).isFalse();
        mvc.perform(get("/shared/v1/" + token));

        assertThat(redis.hasKey(key)).isTrue();
        // Et la seconde résolution rend toujours la même course.
        mvc.perform(get("/shared/v1/" + token))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .forwardedUrl("/race/v1/" + run.id()));
    }

    /**
     * La révocation chasse l'entrée.
     *
     * <p>C'est la seule invalidation dont ce cache a besoin : l'expiration, elle, est portée par
     * la valeur mise en cache et tranchée par le domaine à chaque lecture.
     */
    @Test
    void revokingALinkEvictsItImmediately() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = fixtures.startRun(marie, "PRIVATE");
        String token = issueLinkFor(marie, run);
        mvc.perform(get("/shared/v1/" + token));
        assertThat(redis.hasKey(CacheKey.shareToken(hashOf(token)))).isTrue();

        MvcResult listed = mvc.perform(get("/race/v1/" + run.id() + "/share-links")
                .header("Authorization", marie.bearer())).andReturn();
        String linkId = json.readTree(listed.getResponse().getContentAsString())
                .get("items").get(0).get("id").asText();
        mvc.perform(delete("/share-link/v1/" + linkId).header("Authorization", marie.bearer()))
                .andExpect(status().isNoContent());

        assertThat(redis.hasKey(CacheKey.shareToken(hashOf(token)))).isFalse();
        mvc.perform(get("/shared/v1/" + token)).andExpect(status().isNotFound());
    }

    /** Un jeton inconnu n'écrit rien : sinon qui tâtonne peuplerait le cache à volonté. */
    @Test
    void anUnknownTokenIsNeverCached() throws Exception {
        String invented = "jeton-" + UUID.randomUUID();

        mvc.perform(get("/shared/v1/" + invented)).andExpect(status().isNotFound());

        assertThat(redis.hasKey(CacheKey.shareToken(hashOf(invented)))).isFalse();
    }

    /** Les compteurs d'engagement : lus à chaque affichage de course. */
    @Test
    void engagementCountersAreCachedThenEvictedByALike() throws Exception {
        Account marie = fixtures.newAccount();
        Account paul = fixtures.newAccount();
        Run run = fixtures.startRun(marie);
        String key = CacheKey.activityCounters(run.id());

        mvc.perform(get("/race/v1/" + run.id() + "/likes")
                        .header("Authorization", paul.bearer()))
                .andExpect(jsonPath("$.total").value(0));
        assertThat(redis.hasKey(key)).isTrue();

        mvc.perform(post("/race/v1/" + run.id() + "/likes")
                .header("Authorization", paul.bearer())).andExpect(status().isNoContent());

        // L'invalidation a lieu après commit : sans elle, le compteur resterait à zéro une minute.
        assertThat(redis.hasKey(key)).isFalse();
        mvc.perform(get("/race/v1/" + run.id() + "/likes")
                        .header("Authorization", paul.bearer()))
                .andExpect(jsonPath("$.total").value(1));
    }

    /** Le total de commentaires accompagne la page, et suit les mêmes invalidations. */
    @Test
    void theCommentTotalFollowsTheSameCache() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = fixtures.startRun(marie);

        mvc.perform(post("/race/v1/" + run.id() + "/comments")
                        .header("Authorization", marie.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Bravo\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/race/v1/" + run.id() + "/comments")
                        .header("Authorization", marie.bearer()))
                .andExpect(jsonPath("$.total").value(1));
        assertThat(redis.hasKey(CacheKey.activityCounters(run.id()))).isTrue();
    }

    /** La tête du fil : la requête ouverte à chaque lancement de l'application. */
    @Test
    void theFeedHeadIsCachedButNotThePagesBehindIt() throws Exception {
        Account marie = fixtures.newAccount();
        String key = CacheKey.feedHead(marie.id());

        mvc.perform(get("/feed/v1").header("Authorization", marie.bearer()))
                .andExpect(status().isOk());
        assertThat(redis.hasKey(key)).isTrue();

        redis.delete(key);
        // Une page à curseur est unique : elle n'a personne d'autre à qui servir, donc rien à
        // mémoriser.
        mvc.perform(get("/feed/v1?cursor=" + Instant.now())
                .header("Authorization", marie.bearer())).andExpect(status().isOk());
        assertThat(redis.hasKey(key)).isFalse();
    }

    /** Chaque lecteur a sa tête de fil : deux personnes ne suivent pas les mêmes comptes. */
    @Test
    void twoReadersNeverShareTheirFeedHead() throws Exception {
        Account marie = fixtures.newAccount();
        Account paul = fixtures.newAccount();

        mvc.perform(get("/feed/v1").header("Authorization", marie.bearer()));

        assertThat(redis.hasKey(CacheKey.feedHead(marie.id()))).isTrue();
        assertThat(redis.hasKey(CacheKey.feedHead(paul.id()))).isFalse();
    }

    /**
     * Le verrou anti-stampede : cent lectures simultanées d'une entrée absente ne déclenchent pas
     * cent recalculs.
     *
     * <p>Sans lui, l'expiration de l'entrée des abonnés d'un compte très suivi envoie tout le
     * trafic en cours sur la requête la plus lourde de l'application, en même temps.
     */
    @Test
    void aCacheStampedeCollapsesIntoAlmostOneRecompute() throws Exception {
        String key = CacheKey.PREFIX + "test:stampede:" + UUID.randomUUID();
        var recomputes = new AtomicInteger();
        var ready = new CountDownLatch(1);
        var done = new CountDownLatch(100);

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int reader = 0; reader < 100; reader++) {
                pool.execute(() -> {
                    try {
                        ready.await();
                        cache.getOrLoad(key, String.class, Duration.ofMinutes(1), () -> {
                            recomputes.incrementAndGet();
                            try {
                                Thread.sleep(20);
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                            }
                            return "valeur";
                        });
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            ready.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        }

        // Pas exactement un : les perdants qui relisent trop tôt chargent plutôt que de bloquer,
        // et c'est délibéré — un verrou perdu ne doit pas figer la lecture. Ce qui compte est
        // l'ordre de grandeur : quelques recalculs, pas cent.
        assertThat(recomputes.get()).isLessThan(20);
        assertThat(redis.hasKey(key)).isTrue();
    }
}
