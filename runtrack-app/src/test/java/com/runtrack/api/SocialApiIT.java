package com.runtrack.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/**
 * Le graphe social vu du client, contre une vraie base : abonnement immédiat sur compte
 * public, demande sur compte fermé, et effets d'un blocage.
 */
class SocialApiIT extends ApiIntegrationTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    /** Un compte inscrit et connecté, avec son identifiant et son jeton. */
    private record Account(String id, String token, String handle) {
    }

    private Account newAccount() throws Exception {
        String handle = "s" + COUNTER.incrementAndGet() + System.nanoTime() % 100_000;
        MvcResult created = mvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"handle":"%s","email":"%s@example.com","displayName":"Coureur",
                                 "password":"correcthorsebattery"}
                                """.formatted(handle, handle)))
                .andExpect(status().isCreated())
                .andReturn();
        String id = json.readTree(created.getResponse().getContentAsString()).get("userId").asText();

        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s@example.com","password":"correcthorsebattery"}
                                """.formatted(handle)))
                .andExpect(status().isOk())
                .andReturn();
        String token = json.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
        return new Account(id, token, handle);
    }

    private String bearer(Account account) {
        return "Bearer " + account.token();
    }

    @Test
    void followingAPublicAccountIsImmediate() throws Exception {
        Account marie = newAccount();
        Account paul = newAccount();

        mvc.perform(post("/api/v1/users/" + marie.id() + "/follow").header("Authorization", bearer(paul)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.pending").value(false));

        mvc.perform(get("/api/v1/users/" + marie.id() + "/followers").header("Authorization", bearer(marie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.userIds[0]").value(paul.id()));
    }

    @Test
    void unfollowingRemovesTheLink() throws Exception {
        Account marie = newAccount();
        Account paul = newAccount();
        mvc.perform(post("/api/v1/users/" + marie.id() + "/follow").header("Authorization", bearer(paul)))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/v1/users/" + marie.id() + "/follow").header("Authorization", bearer(paul)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/users/" + marie.id() + "/followers").header("Authorization", bearer(marie)))
                .andExpect(jsonPath("$.count").value(0));
    }

    /** Idempotent : un double clic ne crée pas une seconde demande. */
    @Test
    void followingTwiceIsHarmless() throws Exception {
        Account marie = newAccount();
        Account paul = newAccount();

        mvc.perform(post("/api/v1/users/" + marie.id() + "/follow").header("Authorization", bearer(paul)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/users/" + marie.id() + "/follow").header("Authorization", bearer(paul)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/users/" + marie.id() + "/followers").header("Authorization", bearer(marie)))
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void nobodyFollowsThemselves() throws Exception {
        Account marie = newAccount();

        mvc.perform(post("/api/v1/users/" + marie.id() + "/follow").header("Authorization", bearer(marie)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_FOLLOW"));
    }

    @Test
    void followingNeedsAuthentication() throws Exception {
        Account marie = newAccount();

        mvc.perform(post("/api/v1/users/" + marie.id() + "/follow"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void followingAnUnknownAccountIsNotFound() throws Exception {
        Account paul = newAccount();

        mvc.perform(post("/api/v1/users/018f4c1e-0000-7000-8000-0000000000ff/follow")
                        .header("Authorization", bearer(paul)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    /**
     * Le comportement qui compte : bloquer rompt l'abonnement existant, et empêche de le
     * reformer dans les deux sens.
     */
    @Test
    void blockingBreaksTheFollowAndPreventsANewOne() throws Exception {
        Account marie = newAccount();
        Account paul = newAccount();
        mvc.perform(post("/api/v1/users/" + marie.id() + "/follow").header("Authorization", bearer(paul)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/users/" + paul.id() + "/block").header("Authorization", bearer(marie)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/users/" + marie.id() + "/followers").header("Authorization", bearer(marie)))
                .andExpect(jsonPath("$.count").value(0));

        mvc.perform(post("/api/v1/users/" + marie.id() + "/follow").header("Authorization", bearer(paul)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BLOCKED"));
        mvc.perform(post("/api/v1/users/" + paul.id() + "/follow").header("Authorization", bearer(marie)))
                .andExpect(status().isForbidden());
    }

    /** Débloquer rouvre la possibilité de suivre, mais ne restaure rien. */
    @Test
    void unblockingDoesNotRestoreTheFollow() throws Exception {
        Account marie = newAccount();
        Account paul = newAccount();
        mvc.perform(post("/api/v1/users/" + marie.id() + "/follow").header("Authorization", bearer(paul)));
        mvc.perform(post("/api/v1/users/" + paul.id() + "/block").header("Authorization", bearer(marie)));

        mvc.perform(delete("/api/v1/users/" + paul.id() + "/block").header("Authorization", bearer(marie)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/users/" + marie.id() + "/followers").header("Authorization", bearer(marie)))
                .andExpect(jsonPath("$.count").value(0));
        mvc.perform(post("/api/v1/users/" + marie.id() + "/follow").header("Authorization", bearer(paul)))
                .andExpect(status().isOk());
    }

    @Test
    void nobodyBlocksThemselves() throws Exception {
        Account marie = newAccount();

        mvc.perform(post("/api/v1/users/" + marie.id() + "/block").header("Authorization", bearer(marie)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_BLOCK"));
    }

    @Test
    void followRequestsAreListedForTheAuthenticatedUser() throws Exception {
        Account zoe = newAccount();

        mvc.perform(get("/api/v1/me/follow-requests").header("Authorization", bearer(zoe)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        mvc.perform(get("/api/v1/me/follow-requests")).andExpect(status().isUnauthorized());
    }
}
