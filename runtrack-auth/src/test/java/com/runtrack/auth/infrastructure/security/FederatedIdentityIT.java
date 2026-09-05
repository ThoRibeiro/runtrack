package com.runtrack.auth.infrastructure.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.runtrack.auth.support.AuthIntegrationTest;
import com.runtrack.auth.support.FederatedTokens;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Le pont d'identité, de bout en bout : un jeton de realm entre, un profil existe.
 *
 * <p>C'est le seul test où la chaîne complète du mode fédéré tourne — filtre, accueil du
 * compte, création en base, lecture du profil. Les doubles prouvent l'enchaînement des
 * opérations ; ils ne prouvent pas qu'un identifiant venu d'un jeton devient une ligne de la
 * table {@code users}.
 */
@AutoConfigureMockMvc
@Import(FederatedTokens.TestDecoder.class)
@TestPropertySource(properties = {
        "runtrack.auth.provider=keycloak",
        "runtrack.auth.keycloak.issuer-uri=" + FederatedTokens.ISSUER
})
class FederatedIdentityIT extends AuthIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private static UUID newSubject() {
        return UUID.randomUUID();
    }

    /** Personne ne s'est inscrit : c'est la première requête qui ouvre le profil. */
    @Test
    void aFirstRequestOpensTheProfile() throws Exception {
        UUID marie = newSubject();
        String token = FederatedTokens.signedFor(marie, "marie@example.com", true, "Marie");

        mvc.perform(get("/user/v1/me").header("Authorization", FederatedTokens.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(marie.toString()))
                .andExpect(jsonPath("$.displayName").value("Marie"))
                .andExpect(jsonPath("$.email").value("marie@example.com"))
                // Le fournisseur a vérifié l'adresse : inutile de la faire confirmer à nouveau.
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                // Un pseudo d'attente, dérivé de l'identifiant.
                .andExpect(jsonPath("$.handle").value(
                        "runner-" + marie.toString().replace("-", "").substring(0, 8)));
    }

    /** L'accueil tourne à chaque requête : il doit rester sans effet une fois le profil ouvert. */
    @Test
    void theProfileIsOpenedOnlyOnce() throws Exception {
        UUID paul = newSubject();
        String token = FederatedTokens.signedFor(paul, "paul@example.com", true, "Paul");

        mvc.perform(get("/user/v1/me").header("Authorization", FederatedTokens.bearer(token)))
                .andExpect(status().isOk());

        // Le nom donné entre-temps doit survivre : un second accueil l'écraserait.
        mvc.perform(put("/user/v1/me/handle")
                        .header("Authorization", FederatedTokens.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handle\":\"paul-le-coureur\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/user/v1/me").header("Authorization", FederatedTokens.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handle").value("paul-le-coureur"));
    }

    /** Le parcours « choisis ton pseudo » : le provisoire n'est que provisoire. */
    @Test
    void theProvisionalHandleCanBeReplaced() throws Exception {
        UUID lea = newSubject();
        String token = FederatedTokens.signedFor(lea, "lea@example.com", true, "Léa");
        mvc.perform(get("/user/v1/me").header("Authorization", FederatedTokens.bearer(token)))
                .andExpect(status().isOk());

        mvc.perform(put("/user/v1/me/handle")
                        .header("Authorization", FederatedTokens.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handle\":\"lea\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handle").value("lea"));
    }

    /**
     * Un jeton d'un autre realm est signé — mais pas par le nôtre. Sans la vérification de
     * l'émetteur, il ouvrirait un compte.
     */
    @Test
    void aTokenFromAnotherRealmOpensNothing() throws Exception {
        mvc.perform(get("/user/v1/me")
                        .header("Authorization",
                                FederatedTokens.bearer(FederatedTokens.signedByAnotherRealm(newSubject()))))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Un realm sans scope {@code email} : la requête reste authentifiée — c'est bien le
     * porteur du jeton — mais aucun profil ne peut être ouvert sans adresse.
     */
    @Test
    void aTokenWithoutAnAddressLeavesTheAccountUnopened() throws Exception {
        mvc.perform(get("/user/v1/me")
                        .header("Authorization",
                                FederatedTokens.bearer(FederatedTokens.signedWithoutEmail(newSubject()))))
                .andExpect(status().isNotFound());
    }
}
