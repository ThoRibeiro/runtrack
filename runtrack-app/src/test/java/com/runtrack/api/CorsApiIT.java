package com.runtrack.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * La politique CORS s'applique aux chemins que cette application expose.
 *
 * <p>Ce test existe parce que son absence a coûté cher : la politique était enregistrée sur
 * {@code /api/**}, un préfixe qu'aucune route n'utilise. Le préflight répondait donc 200 sans le
 * moindre en-tête {@code Access-Control-*}, et <b>tout navigateur bloquait chaque requête</b> —
 * le front web ne pouvait pas se connecter du tout.
 *
 * <p>Rien ne l'avait montré, et c'est le plus instructif : {@code curl} ne fait pas de CORS, et
 * les autres tests d'API passent par MockMvc sans jamais poser d'en-tête {@code Origin}. Un
 * navigateur, lui, le voit immédiatement. D'où ces cas, qui envoient un vrai préflight.
 */
@TestPropertySource(properties = "runtrack.cors.allowed-origins=http://localhost:8081")
class CorsApiIT extends ApiIntegrationTest {

    private static final String FRONT = "http://localhost:8081";

    @Autowired
    private MockMvc mvc;

    /** Le chemin de connexion : le tout premier qu'un navigateur appelle. */
    @Test
    void aPreflightOnLoginIsAnswered() throws Exception {
        mvc.perform(options("/auth/v1/login")
                        .header("Origin", FRONT)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONT));
    }

    /**
     * Un chemin de chaque module, parce que le motif est global : le jour où quelqu'un le
     * restreint à nouveau, c'est ici que ça se voit, et non six mois plus tard dans un navigateur.
     */
    @Test
    void everyModuleIsCoveredByThePolicy() throws Exception {
        for (String path : new String[] {
            "/auth/v1/signup", "/race/v1", "/user/v1/me", "/feed/v1",
            "/notification/v1", "/comment/v1/x", "/share-link/v1/x", "/shared/v1/x"
        }) {
            mvc.perform(options(path)
                            .header("Origin", FRONT)
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(header().string("Access-Control-Allow-Origin", FRONT));
        }
    }

    /**
     * Les en-têtes que le client envoie vraiment.
     *
     * <p>{@code Idempotency-Key} porte l'idempotence de l'ingestion et {@code Last-Event-ID} la
     * reprise du direct : un préflight qui les refuse casse l'enregistrement et le suivi, sans
     * qu'aucun statut HTTP ne le dise.
     */
    @Test
    void theHeadersTheClientSendsAreAllowed() throws Exception {
        mvc.perform(options("/race/v1/x/points")
                        .header("Origin", FRONT)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers",
                                "authorization,content-type,idempotency-key,x-correlation-id"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONT));
    }

    /**
     * {@code X-Correlation-Id} doit être exposé, sinon le navigateur le masque au JavaScript —
     * et c'est la référence que l'utilisateur cite quand il signale un problème.
     */
    @Test
    void theCorrelationIdIsExposedToTheBrowser() throws Exception {
        mvc.perform(options("/auth/v1/login")
                        .header("Origin", FRONT)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().string("Access-Control-Expose-Headers", "X-Correlation-Id"));
    }

    /** Une origine hors de la liste n'obtient rien : la politique est une liste, jamais `*`. */
    @Test
    void anUnknownOriginIsRefused() throws Exception {
        mvc.perform(options("/auth/v1/login")
                        .header("Origin", "https://ailleurs.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
