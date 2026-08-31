package com.runtrack.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.runtrack.auth.usecases.service.AuthenticatedSession;
import com.runtrack.auth.usecases.service.Authentication;
import com.runtrack.auth.usecases.port.PasswordHasher;
import com.runtrack.auth.usecases.model.credential.Password;
import com.runtrack.auth.support.AuthIntegrationTest;
import com.runtrack.shared.error.ForbiddenException;
import com.runtrack.shared.id.UserId;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * L'authentification de bout en bout : Argon2 réel, jetons persistés, rotation et
 * révocation vérifiées en base.
 *
 * <p>Les tests unitaires utilisent un hachage trivial, ce qui est le bon choix pour tester
 * un cas d'usage. Ici on veut au contraire la vraie implémentation, parce que c'est elle
 * qui tourne en production — et un mot de passe qui ne se revalide pas après hachage ne se
 * verrait nulle part ailleurs.
 */
class AuthenticationIT extends AuthIntegrationTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final Password PASSWORD = new Password("correcthorsebattery");

    @Autowired
    private Authentication authentication;

    @Autowired
    private PasswordHasher hasher;

    private String uniqueEmail() {
        return "marie" + COUNTER.incrementAndGet() + System.nanoTime() % 100_000 + "@example.com";
    }

    private String signUp(String email) {
        String handle = "u" + email.substring(0, email.indexOf('@'));
        authentication.signUp(handle, email, "Marie", PASSWORD);
        return email;
    }

    @Test
    void argon2RoundTripsARealPassword() {
        var hash = hasher.hash(PASSWORD);

        assertThat(hash.value()).startsWith("$argon2id$");
        assertThat(hasher.matches(PASSWORD, hash)).isTrue();
        assertThat(hasher.matches(new Password("un-autre-mot-de-passe"), hash)).isFalse();
    }

    /** Le sel rend deux empreintes du même mot de passe différentes. */
    @Test
    void twoHashesOfTheSamePasswordDiffer() {
        assertThat(hasher.hash(PASSWORD).value()).isNotEqualTo(hasher.hash(PASSWORD).value());
    }

    @Test
    void signUpThenLogInIssuesAWorkingSession() {
        String email = signUp(uniqueEmail());

        AuthenticatedSession session = authentication.logIn(email, PASSWORD);

        assertThat(session.accessToken()).isNotBlank();
        assertThat(session.refreshToken()).isNotBlank();
        assertThat(session.expiresInSeconds()).isPositive();
    }

    @Test
    void theAccessTokenIsASignedJwtCarryingTheUser() {
        String email = signUp(uniqueEmail());

        String accessToken = authentication.logIn(email, PASSWORD).accessToken();

        assertThat(accessToken.split("\\.")).hasSize(3);
        String payload = new String(java.util.Base64.getUrlDecoder()
                .decode(accessToken.split("\\.")[1]), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(payload).contains("\"iss\":\"runtrack\"").contains("\"sub\"");
    }

    @Test
    void refreshRotatesTheStoredToken() {
        String email = signUp(uniqueEmail());
        AuthenticatedSession first = authentication.logIn(email, PASSWORD);

        AuthenticatedSession second = authentication.refresh(first.refreshToken());

        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> authentication.refresh(first.refreshToken()));
    }

    /** La détection de vol doit survivre à un aller-retour en base, pas seulement en mémoire. */
    @Test
    void replayingAStolenTokenRevokesTheWholeFamilyInTheDatabase() {
        String email = signUp(uniqueEmail());
        AuthenticatedSession stolen = authentication.logIn(email, PASSWORD);
        AuthenticatedSession legitimate = authentication.refresh(stolen.refreshToken());

        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> authentication.refresh(stolen.refreshToken()))
                .extracting(ForbiddenException::code)
                .isEqualTo("REFRESH_TOKEN_REUSED");

        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> authentication.refresh(legitimate.refreshToken()));
    }

    @Test
    void aWrongPasswordIsRefusedByTheRealHasher() {
        String email = signUp(uniqueEmail());

        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> authentication.logIn(email, new Password("mauvais-mot-de-passe")))
                .extracting(ForbiddenException::code)
                .isEqualTo("BAD_CREDENTIALS");
    }

    @Test
    void loggingOutKillsTheStoredSession() {
        String email = signUp(uniqueEmail());
        AuthenticatedSession session = authentication.logIn(email, PASSWORD);

        authentication.logOut(session.refreshToken());

        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> authentication.refresh(session.refreshToken()));
    }

    @Test
    void anUnknownUserProducesNoSession() {
        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> authentication.logIn("jamais-inscrit@example.com", PASSWORD));
    }

    @Test
    void signUpReturnsAUsableIdentifier() {
        String email = uniqueEmail();
        String handle = "u" + email.substring(0, email.indexOf('@'));

        UserId id = authentication.signUp(handle, email, "Marie", PASSWORD);

        assertThat(id).isNotNull();
        assertThat(authentication.logIn(email, PASSWORD).accessToken()).contains(".");
    }
}
