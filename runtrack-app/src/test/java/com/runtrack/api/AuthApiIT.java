package com.runtrack.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Les endpoints d'authentification vus du client : codes HTTP, corps
 * {@code application/problem+json} et code métier stable.
 */
class AuthApiIT extends ApiIntegrationTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private String uniqueHandle() {
        return "marie" + COUNTER.incrementAndGet() + System.nanoTime() % 100_000;
    }

    private String signUpBody(String handle, String password) {
        return """
                {"handle":"%s","email":"%s@example.com","displayName":"Marie","password":"%s"}
                """.formatted(handle, handle, password);
    }

    private String signUp(String handle) throws Exception {
        mvc.perform(post("/auth/v1/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpBody(handle, "correcthorsebattery")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").exists());
        return handle;
    }

    private JsonNode logIn(String handle) throws Exception {
        MvcResult result = mvc.perform(post("/auth/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s@example.com","password":"correcthorsebattery"}
                                """.formatted(handle)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void signUpThenLogInThenReadOwnProfile() throws Exception {
        String handle = signUp(uniqueHandle());
        String accessToken = logIn(handle).get("accessToken").asText();

        mvc.perform(get("/user/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handle").value(handle))
                .andExpect(jsonPath("$.email").value(handle + "@example.com"))
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"));
    }

    /** 401 : la ressource demande une identité, et aucune n'a été présentée. */
    @Test
    void readingOwnProfileWithoutATokenIsUnauthorized() throws Exception {
        mvc.perform(get("/user/v1/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void anInvalidTokenIsTreatedAsNoTokenAtAll() throws Exception {
        mvc.perform(get("/user/v1/me").header("Authorization", "Bearer pas-un-jwt"))
                .andExpect(status().isUnauthorized());
    }

    /** 403 avec un code métier stable, identique que l'adresse existe ou non. */
    @Test
    void aWrongPasswordIsForbiddenWithAProblemDetail() throws Exception {
        String handle = signUp(uniqueHandle());

        mvc.perform(post("/auth/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s@example.com","password":"un-mauvais-mot-de-passe"}
                                """.formatted(handle)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"))
                .andExpect(jsonPath("$.type").value(containsString("bad-credentials")));
    }

    @Test
    void anUnknownEmailAnswersExactlyLikeAWrongPassword() throws Exception {
        mvc.perform(post("/auth/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jamais-inscrit@example.com","password":"correcthorsebattery"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    /** 409 : l'identifiant public est pris. */
    @Test
    void aDuplicateHandleIsAConflict() throws Exception {
        String handle = signUp(uniqueHandle());

        mvc.perform(post("/auth/v1/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpBody(handle, "correcthorsebattery")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HANDLE_TAKEN"));
    }

    /** 422 : la validation Jakarta refuse la requête avant tout traitement. */
    @Test
    void aTooShortPasswordIsUnprocessable() throws Exception {
        mvc.perform(post("/auth/v1/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpBody(uniqueHandle(), "court")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void aMalformedEmailIsUnprocessable() throws Exception {
        mvc.perform(post("/auth/v1/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"handle":"%s","email":"pas-une-adresse","displayName":"Marie",
                                 "password":"correcthorsebattery"}
                                """.formatted(uniqueHandle())))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void refreshRotatesAndTheOldTokenStopsWorking() throws Exception {
        String handle = signUp(uniqueHandle());
        String first = logIn(handle).get("refreshToken").asText();

        MvcResult rotated = mvc.perform(post("/auth/v1/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(first)))
                .andExpect(status().isOk())
                .andReturn();
        String second = json.readTree(rotated.getResponse().getContentAsString())
                .get("refreshToken").asText();

        mvc.perform(post("/auth/v1/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(first)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"));

        // Le successeur est mort avec la famille : c'est le but de la révocation.
        mvc.perform(post("/auth/v1/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(second)))
                .andExpect(status().isForbidden());
    }

    @Test
    void logOutClosesTheSession() throws Exception {
        String handle = signUp(uniqueHandle());
        String refreshToken = logIn(handle).get("refreshToken").asText();

        mvc.perform(post("/auth/v1/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)))
                .andExpect(status().isNoContent());

        mvc.perform(post("/auth/v1/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)))
                .andExpect(status().isForbidden());
    }

    /** 404 : un lien de confirmation qui n'a jamais été émis. */
    @Test
    void anUnknownVerificationTokenIsNotFound() throws Exception {
        mvc.perform(get("/auth/v1/verify-email").param("token", "jamais-emis"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TOKEN_UNKNOWN"));
    }

    /** 202 quelle que soit l'issue : l'endpoint ne doit pas énumérer les comptes. */
    @Test
    void forgotPasswordAcceptsEvenAnUnknownAddress() throws Exception {
        mvc.perform(post("/auth/v1/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jamais-inscrit@example.com\"}"))
                .andExpect(status().isAccepted());
    }
}
