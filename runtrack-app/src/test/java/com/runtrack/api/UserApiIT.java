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

/** Les endpoints de profil : contrat, autorisation et matrice d'erreurs. */
class UserApiIT extends ApiIntegrationTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private String uniqueHandle() {
        return "paul" + COUNTER.incrementAndGet() + System.nanoTime() % 100_000;
    }

    /** Inscrit, confirme l'adresse via le lien, puis rend un jeton d'accès utilisable. */
    private String activeUserToken(String handle) throws Exception {
        mvc.perform(post("/auth/v1/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"handle":"%s","email":"%s@example.com","displayName":"Paul",
                                 "password":"correcthorsebattery"}
                                """.formatted(handle, handle)))
                .andExpect(status().isCreated());

        MvcResult login = mvc.perform(post("/auth/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s@example.com","password":"correcthorsebattery"}
                                """.formatted(handle)))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void aPublicProfileIsReadableWithoutAuthenticationAndHidesTheEmail() throws Exception {
        String handle = uniqueHandle();
        activeUserToken(handle);

        mvc.perform(get("/user/v1/" + handle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handle").value(handle))
                .andExpect(jsonPath("$.displayName").value("Paul"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist());
    }

    @Test
    void anUnknownHandleIsNotFound() throws Exception {
        mvc.perform(get("/user/v1/personnequinexistepas"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    /** Un compte non confirmé ne se modifie pas : le domaine refuse, l'API rend 409. */
    @Test
    void editingBeforeConfirmingTheEmailIsAConflict() throws Exception {
        String token = activeUserToken(uniqueHandle());

        mvc.perform(patch("/user/v1/me")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Paul D.\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));
    }

    @Test
    void anEmptyDisplayNameIsUnprocessable() throws Exception {
        String token = activeUserToken(uniqueHandle());

        mvc.perform(patch("/user/v1/me")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"  \"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void anInvalidHandleIsUnprocessable() throws Exception {
        String token = activeUserToken(uniqueHandle());

        mvc.perform(put("/user/v1/me/handle")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handle\":\"un handle invalide\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void physiologyIsReadableOnlyByItsOwnerAndStartsEmpty() throws Exception {
        String token = activeUserToken(uniqueHandle());

        mvc.perform(get("/user/v1/me/physiology").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weightKilograms").doesNotExist());

        mvc.perform(get("/user/v1/me/physiology")).andExpect(status().isUnauthorized());
    }

    @Test
    void searchNeedsAuthentication() throws Exception {
        mvc.perform(get("/user/v1").param("search", "paul"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void searchFindsAProfileByHandle() throws Exception {
        String handle = uniqueHandle();
        String token = activeUserToken(handle);

        mvc.perform(get("/user/v1").param("search", handle)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].handle").value(handle));
    }

    @Test
    void deletingOwnAccountNeedsAuthentication() throws Exception {
        mvc.perform(delete("/user/v1/me")).andExpect(status().isUnauthorized());
    }
}
