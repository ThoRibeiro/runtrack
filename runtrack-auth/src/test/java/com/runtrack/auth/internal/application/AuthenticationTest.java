package com.runtrack.auth.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.runtrack.auth.event.UserAuthenticated;
import com.runtrack.auth.internal.application.fixture.AuthDoubles;
import com.runtrack.auth.internal.domain.credential.Password;
import com.runtrack.auth.internal.domain.token.RefreshToken;
import com.runtrack.auth.internal.domain.token.TokenPurpose;
import com.runtrack.shared.error.ConflictException;
import com.runtrack.shared.error.ForbiddenException;
import com.runtrack.shared.error.NotFoundException;
import com.runtrack.shared.id.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class AuthenticationTest {

    private static final Instant NOW = Instant.parse("2026-08-29T08:00:00Z");
    private static final Password PASSWORD = new Password("correcthorsebattery");
    private static final String EMAIL = "marie@example.com";

    private AuthDoubles.Users users;
    private AuthDoubles.RefreshTokens refreshTokens;
    private AuthDoubles.SingleUseTokens singleUseTokens;
    private AuthDoubles.Mailer mailer;
    private List<Object> published;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        users = new AuthDoubles.Users();
        refreshTokens = new AuthDoubles.RefreshTokens();
        singleUseTokens = new AuthDoubles.SingleUseTokens(clock);
        mailer = new AuthDoubles.Mailer();
        published = new ArrayList<>();
        ApplicationEventPublisher publisher = published::add;

        authentication = new Authentication(users, new AuthDoubles.Credential(), refreshTokens,
                singleUseTokens, new AuthDoubles.Hasher(), new AuthDoubles.AccessTokens(), mailer,
                publisher, clock, AuthDoubles.seededRandom());
    }

    private UserId signUpMarie() {
        return authentication.signUp("marie", EMAIL, "Marie", PASSWORD);
    }

    @Nested
    class SigningUp {

        @Test
        void createsTheProfileAndMailsAVerificationLink() {
            UserId id = signUpMarie();

            assertThat(users.exists(id)).isTrue();
            assertThat(mailer.sent).singleElement().satisfies(mail -> {
                assertThat(mail.address()).isEqualTo(EMAIL);
                assertThat(mail.purpose()).isEqualTo(TokenPurpose.EMAIL_VERIFICATION);
                assertThat(mail.secret()).isNotBlank();
            });
        }

        @Test
        void confirmingTheEmailActivatesTheProfile() {
            UserId id = signUpMarie();

            authentication.confirmEmail(mailer.sent.getFirst().secret());

            assertThat(users.confirmed).containsExactly(id);
        }

        @Test
        void aVerificationLinkWorksOnlyOnce() {
            signUpMarie();
            String secret = mailer.sent.getFirst().secret();
            authentication.confirmEmail(secret);

            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> authentication.confirmEmail(secret))
                    .extracting(ConflictException::code)
                    .isEqualTo("TOKEN_ALREADY_USED");
        }

        @Test
        void anUnknownLinkIsRefused() {
            assertThatExceptionOfType(NotFoundException.class)
                    .isThrownBy(() -> authentication.confirmEmail("jamais-emis"))
                    .extracting(NotFoundException::code)
                    .isEqualTo("TOKEN_UNKNOWN");
        }
    }

    @Nested
    class LoggingIn {

        @Test
        void returnsASessionAndAnnouncesIt() {
            UserId id = signUpMarie();

            AuthenticatedSession session = authentication.logIn(EMAIL, PASSWORD);

            assertThat(session.accessToken()).contains(id.toString());
            assertThat(session.refreshToken()).isNotBlank();
            assertThat(session.expiresInSeconds()).isEqualTo(900);
            assertThat(published).singleElement().isInstanceOf(UserAuthenticated.class);
        }

        /**
         * Adresse inconnue et mot de passe faux donnent exactement la même erreur. Les
         * distinguer ferait du formulaire de connexion un oracle disant quelles adresses ont
         * un compte.
         */
        @Test
        void tellsNothingAboutWhichHalfWasWrong() {
            signUpMarie();

            var wrongPassword = catchCode(() -> authentication.logIn(EMAIL, new Password("mauvais-mot-de-passe")));
            var unknownEmail = catchCode(() -> authentication.logIn("inconnu@example.com", PASSWORD));

            assertThat(wrongPassword).isEqualTo("BAD_CREDENTIALS").isEqualTo(unknownEmail);
        }

        @Test
        void aFailedLoginOpensNoSession() {
            signUpMarie();

            assertThatExceptionOfType(ForbiddenException.class)
                    .isThrownBy(() -> authentication.logIn(EMAIL, new Password("mauvais-mot-de-passe")));

            assertThat(refreshTokens.all()).isEmpty();
        }
    }

    @Nested
    class RotatingTheSession {

        @Test
        void issuesASuccessorInTheSameFamily() {
            signUpMarie();
            AuthenticatedSession first = authentication.logIn(EMAIL, PASSWORD);

            AuthenticatedSession second = authentication.refresh(first.refreshToken());

            assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
            assertThat(refreshTokens.all()).extracting(RefreshToken::familyId).hasSize(2).containsOnly(
                    refreshTokens.all().getFirst().familyId());
        }

        @Test
        void theOldTokenStopsWorking() {
            signUpMarie();
            AuthenticatedSession first = authentication.logIn(EMAIL, PASSWORD);
            authentication.refresh(first.refreshToken());

            assertThatExceptionOfType(ForbiddenException.class)
                    .isThrownBy(() -> authentication.refresh(first.refreshToken()));
        }

        /**
         * Le cœur de la détection de vol : rejouer un jeton consommé révoque <em>toute</em>
         * la chaîne. Refuser le seul jeton rejoué laisserait celle du voleur intacte.
         */
        @Test
        void replayingAConsumedTokenKillsTheWholeFamily() {
            signUpMarie();
            AuthenticatedSession stolen = authentication.logIn(EMAIL, PASSWORD);
            AuthenticatedSession legitimate = authentication.refresh(stolen.refreshToken());

            assertThatExceptionOfType(ForbiddenException.class)
                    .isThrownBy(() -> authentication.refresh(stolen.refreshToken()))
                    .extracting(ForbiddenException::code)
                    .isEqualTo("REFRESH_TOKEN_REUSED");

            assertThat(refreshTokens.all()).allMatch(RefreshToken::isRevoked);
            assertThatExceptionOfType(ForbiddenException.class)
                    .isThrownBy(() -> authentication.refresh(legitimate.refreshToken()));
        }

        @Test
        void anUnknownRefreshTokenIsRefused() {
            assertThatExceptionOfType(ForbiddenException.class)
                    .isThrownBy(() -> authentication.refresh("jamais-emis"))
                    .extracting(ForbiddenException::code)
                    .isEqualTo("REFRESH_TOKEN_UNKNOWN");
        }

        @Test
        void loggingOutRevokesTheFamily() {
            signUpMarie();
            AuthenticatedSession session = authentication.logIn(EMAIL, PASSWORD);

            authentication.logOut(session.refreshToken());

            assertThat(refreshTokens.all()).allMatch(RefreshToken::isRevoked);
        }

        @Test
        void loggingOutWithAnUnknownTokenIsHarmless() {
            assertThat(catchCode(() -> authentication.logOut("jamais-emis"))).isNull();
        }
    }

    @Nested
    class ResettingThePassword {

        @Test
        void mailsALinkAndLetsTheUserLogInWithTheNewPassword() {
            signUpMarie();
            authentication.requestPasswordReset(EMAIL);
            String secret = mailer.sent.getLast().secret();

            authentication.resetPassword(secret, new Password("un-nouveau-mot-de-passe"));

            assertThat(authentication.logIn(EMAIL, new Password("un-nouveau-mot-de-passe"))).isNotNull();
            assertThatExceptionOfType(ForbiddenException.class)
                    .isThrownBy(() -> authentication.logIn(EMAIL, PASSWORD));
        }

        /** Les sessions en cours pourraient être celles du voleur : on les coupe toutes. */
        @Test
        void revokesEverySessionInFlight() {
            signUpMarie();
            AuthenticatedSession before = authentication.logIn(EMAIL, PASSWORD);
            authentication.requestPasswordReset(EMAIL);

            authentication.resetPassword(mailer.sent.getLast().secret(), new Password("un-nouveau-mot-de-passe"));

            assertThatExceptionOfType(ForbiddenException.class)
                    .isThrownBy(() -> authentication.refresh(before.refreshToken()));
        }

        /** Aucune réponse ne dit si l'adresse existe, sinon l'endpoint énumère les comptes. */
        @Test
        void staysSilentAboutUnknownAddresses() {
            authentication.requestPasswordReset("inconnu@example.com");

            assertThat(mailer.sent).isEmpty();
        }

        @Test
        void aVerificationLinkCannotResetAPassword() {
            signUpMarie();
            String verificationSecret = mailer.sent.getFirst().secret();

            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> authentication.resetPassword(
                            verificationSecret, new Password("un-nouveau-mot-de-passe")))
                    .extracting(ConflictException::code)
                    .isEqualTo("TOKEN_WRONG_PURPOSE");
        }

        @Test
        void issuingANewLinkInvalidatesThePreviousOne() {
            signUpMarie();
            authentication.requestPasswordReset(EMAIL);
            String first = mailer.sent.getLast().secret();
            authentication.requestPasswordReset(EMAIL);

            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> authentication.resetPassword(first, new Password("un-nouveau-mot-de-passe")));
        }
    }

    private static String catchCode(Runnable action) {
        try {
            action.run();
            return null;
        } catch (ForbiddenException e) {
            return e.code();
        } catch (ConflictException | NotFoundException e) {
            return e.code();
        }
    }
}
