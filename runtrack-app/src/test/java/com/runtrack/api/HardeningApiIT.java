package com.runtrack.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.runtrack.api.CourseFixtures.Account;
import com.runtrack.api.CourseFixtures.Run;
import com.runtrack.platform.observability.CorrelationId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/**
 * Le durcissement du §9 et l'observabilité du §12, contre l'application assemblée.
 *
 * <p>Les quotas ne se vérifient qu'ici : ils s'appuient sur l'atomicité d'un {@code INCR} dans
 * Dragonfly, et aucun double en mémoire n'en témoigne.
 */
class HardeningApiIT extends ApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private CourseFixtures fixtures;

    @BeforeEach
    void setUpFixtures() {
        fixtures = new CourseFixtures(mvc, json);
    }

    /** L'identifiant fourni par l'appelant est celui qui repart : c'est ce qu'il pourra citer. */
    @Test
    void theCorrelationIdentifierIsEchoedBack() throws Exception {
        String provided = "trace-" + UUID.randomUUID();

        mvc.perform(get("/feed/v1").header(CorrelationId.HEADER, provided))
                .andExpect(header().string(CorrelationId.HEADER, provided));
    }

    /** Sans en-tête, il est tiré : une requête sans corrélation n'existe pas. */
    @Test
    void anIdentifierIsMintedWhenTheCallerBringsNone() throws Exception {
        MvcResult answered = mvc.perform(get("/feed/v1")).andReturn();

        assertThat(answered.getResponse().getHeader(CorrelationId.HEADER)).isNotBlank();
    }

    /** Les en-têtes de sécurité du §9, sur une réponse quelconque. */
    @Test
    void everyResponseCarriesItsSecurityHeaders() throws Exception {
        mvc.perform(get("/feed/v1"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'"));
    }

    /**
     * L'unique exception à la politique ci-dessus, et la raison qu'elle a d'exister : Swagger UI
     * est du HTML, et {@code default-src 'none'} lui retirerait sa feuille de style comme son
     * bundle — une page blanche qu'aucune erreur serveur n'expliquerait. L'assertion porte sur ce
     * que la page doit pouvoir charger, pas sur la chaîne exacte, qui peut encore se resserrer.
     */
    @Test
    void theSwaggerUserInterfaceIsAllowedToLoadItsOwnAssets() throws Exception {
        String policy = mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Content-Security-Policy");

        assertThat(policy)
                .contains("default-src 'self'")
                .contains("script-src 'self'")
                .contains("style-src 'self' 'unsafe-inline'");
    }

    /**
     * Le quota de connexion, par compte.
     *
     * <p>Il compte les tentatives ratées : ne compter que les succès reviendrait à ne rien
     * compter, puisque c'est précisément l'échec répété qu'on cherche à arrêter.
     */
    @Test
    void repeatedFailedLoginsAreEventuallyRefused() throws Exception {
        String email = "brute-" + UUID.randomUUID() + "@example.com";
        String body = """
                {"email":"%s","password":"mauvais-mot-de-passe"}
                """.formatted(email);

        int refusedAt = -1;
        for (int attempt = 1; attempt <= 15 && refusedAt < 0; attempt++) {
            int status = mvc.perform(post("/auth/v1/login")
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andReturn().getResponse().getStatus();
            if (status == 429) {
                refusedAt = attempt;
            }
        }

        assertThat(refusedAt).isPositive();
        mvc.perform(post("/auth/v1/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                // Le code métier, pas le statut : c'est lui que le client teste (§8).
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
    }

    /**
     * Un quota atteint sur un compte n'empêche pas un autre de se connecter.
     *
     * <p>C'est la propriété qui rend le quota par compte utilisable : sans elle, saturer le
     * compte d'un tiers suffirait à bloquer tout le monde.
     *
     * <p>Le quota par <em>adresse</em>, lui, n'est pas vérifié ici et ne peut pas l'être :
     * MockMvc fait partir toutes les requêtes de {@code 127.0.0.1}, si bien que le compteur par
     * IP est celui de la suite entière. Il est donc désarmé dans le profil de test — et c'est
     * précisément ce partage d'adresse qui justifie qu'en production il soit vingt fois plus
     * large que celui par compte.
     */
    @Test
    void oneAccountsQuotaDoesNotBlockAnother() throws Exception {
        String saturated = """
                {"email":"sature-%s@example.com","password":"mauvais"}
                """.formatted(UUID.randomUUID());
        for (int attempt = 0; attempt < 12; attempt++) {
            mvc.perform(post("/auth/v1/login")
                    .contentType(MediaType.APPLICATION_JSON).content(saturated));
        }

        Account marie = fixtures.newAccount();
        assertThat(marie.token()).isNotBlank();
    }

    /** L'ingestion est bridée par course, et le message le dit sans ambiguïté. */
    @Test
    void aClientLoopingOnPointsIsSlowedDown() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = fixtures.startRun(marie);

        int refusedAt = -1;
        for (int attempt = 1; attempt <= 80 && refusedAt < 0; attempt++) {
            int status = fixtures.ingest(marie, run, 1, 2).getResponse().getStatus();
            if (status == 429) {
                refusedAt = attempt;
            }
        }

        assertThat(refusedAt).isPositive();
        assertThat(json.readTree(fixtures.ingest(marie, run, 1, 2)
                .getResponse().getContentAsString()).get("code").asText())
                .isEqualTo("TOO_MANY_BATCHES");
    }

    /** Les métriques métier du §12 existent réellement, et sont exposées. */
    @Test
    void theBusinessMetricsAreExposed() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = fixtures.startRun(marie);
        fixtures.ingest(marie, run, 1, 5);

        MvcResult scraped = mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk()).andReturn();
        String metrics = scraped.getResponse().getContentAsString();

        assertThat(metrics)
                .contains("runtrack_points_accepted")
                .contains("runtrack_ingestion_seconds")
                .contains("runtrack_live_subscribers")
                .contains("runtrack_events_incomplete");
    }

    /** Liveness et readiness séparées : elles ne répondent pas à la même question (§12). */
    @Test
    void livenessAndReadinessAreSeparate() throws Exception {
        mvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
