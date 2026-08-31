package com.runtrack.auth.usecases.service;

import com.runtrack.auth.event.UserAuthenticated;
import com.runtrack.auth.usecases.port.AccessTokenIssuer;
import com.runtrack.auth.usecases.port.AuthMailer;
import com.runtrack.auth.usecases.port.CredentialsRepository;
import com.runtrack.auth.usecases.port.PasswordHasher;
import com.runtrack.auth.usecases.port.RefreshTokenRepository;
import com.runtrack.auth.usecases.port.SingleUseTokenRepository;
import com.runtrack.auth.usecases.model.credential.Credentials;
import com.runtrack.auth.usecases.model.credential.Password;
import com.runtrack.auth.usecases.model.token.OpaqueToken;
import com.runtrack.auth.usecases.model.token.RefreshToken;
import com.runtrack.auth.usecases.model.token.SingleUseToken;
import com.runtrack.auth.usecases.model.token.TokenPurpose;
import com.runtrack.shared.error.ConflictException;
import com.runtrack.shared.error.ForbiddenException;
import com.runtrack.shared.error.NotFoundException;
import com.runtrack.shared.id.UserId;
import com.runtrack.user.NewUser;
import com.runtrack.user.UserApi;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Inscription, connexion, rotation des sessions et déconnexion. */
@Service
public class Authentication {

    public static final Duration REFRESH_LIFETIME = Duration.ofDays(30);

    private final UserApi users;
    private final CredentialsRepository credentials;
    private final RefreshTokenRepository refreshTokens;
    private final SingleUseTokenRepository singleUseTokens;
    private final PasswordHasher hasher;
    private final AccessTokenIssuer accessTokens;
    private final AuthMailer mailer;
    private final SessionRevocation revocation;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final RandomGenerator random;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public Authentication(UserApi users, CredentialsRepository credentials,
            RefreshTokenRepository refreshTokens, SingleUseTokenRepository singleUseTokens,
            PasswordHasher hasher, AccessTokenIssuer accessTokens, AuthMailer mailer,
            SessionRevocation revocation, ApplicationEventPublisher events,
            Clock clock, RandomGenerator random) {
        this.users = users;
        this.credentials = credentials;
        this.refreshTokens = refreshTokens;
        this.singleUseTokens = singleUseTokens;
        this.hasher = hasher;
        this.accessTokens = accessTokens;
        this.mailer = mailer;
        this.revocation = revocation;
        this.events = events;
        this.clock = clock;
        this.random = random;
    }

    @Transactional
    public UserId signUp(String handle, String email, String displayName, Password password) {
        UserId userId = users.register(new NewUser(handle, email, displayName));
        credentials.save(Credentials.create(userId, hasher.hash(password), clock.instant()));
        mailer.sendEmailVerification(email, issueSingleUse(userId, TokenPurpose.EMAIL_VERIFICATION));
        return userId;
    }

    /**
     * Vérifie le mot de passe et ouvre une session.
     *
     * <p>Adresse inconnue et mot de passe faux rendent la même erreur : distinguer les deux
     * transforme le formulaire de connexion en oracle qui dit quelles adresses ont un compte.
     */
    @Transactional
    public AuthenticatedSession logIn(String email, Password password) {
        UserId userId = users.idOfEmail(email).orElseThrow(Authentication::badCredentials);
        Credentials stored = credentials.findByUserId(userId).orElseThrow(Authentication::badCredentials);
        if (!hasher.matches(password, stored.passwordHash())) {
            throw badCredentials();
        }

        Instant now = clock.instant();
        events.publishEvent(new UserAuthenticated(userId, now));
        return openSession(userId, now);
    }

    /**
     * Rejoue la rotation : le jeton présenté est consommé, un successeur est émis.
     *
     * <p>Un jeton déjà consommé qui se represente veut dire qu'une copie circule. On révoque
     * alors <em>toute la famille</em> : refuser le seul jeton rejoué laisserait la chaîne du
     * voleur intacte.
     */
    @Transactional
    public AuthenticatedSession refresh(String presentedSecret) {
        RefreshToken presented = refreshTokens.findByHash(OpaqueToken.hashOf(presentedSecret))
                .orElseThrow(() -> new ForbiddenException(
                        "REFRESH_TOKEN_UNKNOWN", "Session inconnue, reconnexion nécessaire"));

        if (presented.wasConsumed()) {
            // Transaction séparée : le refus qui suit annulerait sinon la révocation.
            revocation.revokeFamily(presented.familyId());
            throw new ForbiddenException("REFRESH_TOKEN_REUSED",
                    "Ce jeton a déjà servi : la session entière a été révoquée par précaution");
        }

        Instant now = clock.instant();
        presented.consume(now);
        refreshTokens.save(presented);

        OpaqueToken next = OpaqueToken.generate(random);
        refreshTokens.save(presented.succeededBy(
                UUID.randomUUID(), next.hash(), now, now.plus(REFRESH_LIFETIME)));

        return new AuthenticatedSession(
                accessTokens.issueFor(presented.userId()), next.secret(), accessTokens.lifetime().toSeconds());
    }

    @Transactional
    public void logOut(String presentedSecret) {
        refreshTokens.findByHash(OpaqueToken.hashOf(presentedSecret))
                .ifPresent(token -> refreshTokens.revokeFamily(token.familyId()));
    }

    @Transactional
    public void confirmEmail(String presentedSecret) {
        SingleUseToken token = requireSingleUse(presentedSecret, TokenPurpose.EMAIL_VERIFICATION);
        token.consume(clock.instant());
        singleUseTokens.save(token);
        users.confirmEmail(token.userId());
    }

    /**
     * Envoie un lien de réinitialisation, sans jamais dire si l'adresse existe : la réponse
     * est la même dans les deux cas, sinon l'endpoint énumère les comptes.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        Optional<UserId> userId = users.idOfEmail(email);
        userId.ifPresent(id -> mailer.sendPasswordReset(
                email, issueSingleUse(id, TokenPurpose.PASSWORD_RESET)));
    }

    /** Change le mot de passe et déconnecte toutes les sessions : elles pourraient être celles du voleur. */
    @Transactional
    public void resetPassword(String presentedSecret, Password newPassword) {
        SingleUseToken token = requireSingleUse(presentedSecret, TokenPurpose.PASSWORD_RESET);
        Instant now = clock.instant();
        token.consume(now);
        singleUseTokens.save(token);

        Credentials stored = credentials.findByUserId(token.userId())
                .orElseThrow(() -> new NotFoundException("CREDENTIALS_NOT_FOUND", "Compte introuvable"));
        stored.changePassword(hasher.hash(newPassword), now);
        credentials.save(stored);
        refreshTokens.revokeAllOf(token.userId());
    }

    private AuthenticatedSession openSession(UserId userId, Instant now) {
        OpaqueToken refresh = OpaqueToken.generate(random);
        refreshTokens.save(RefreshToken.openFamily(
                UUID.randomUUID(), userId, refresh.hash(), now, now.plus(REFRESH_LIFETIME)));
        return new AuthenticatedSession(
                accessTokens.issueFor(userId), refresh.secret(), accessTokens.lifetime().toSeconds());
    }

    private String issueSingleUse(UserId userId, TokenPurpose purpose) {
        singleUseTokens.consumeAllOf(userId, purpose);
        OpaqueToken token = OpaqueToken.generate(random);
        singleUseTokens.save(SingleUseToken.issue(
                UUID.randomUUID(), userId, purpose, token.hash(), clock.instant()));
        return token.secret();
    }

    private SingleUseToken requireSingleUse(String presentedSecret, TokenPurpose purpose) {
        SingleUseToken token = singleUseTokens.findByHash(OpaqueToken.hashOf(presentedSecret))
                .orElseThrow(() -> new NotFoundException("TOKEN_UNKNOWN", "Ce lien n'est pas valide"));
        if (token.purpose() != purpose) {
            // Un jeton de confirmation ne doit pas pouvoir servir à changer un mot de passe.
            throw new ConflictException("TOKEN_WRONG_PURPOSE", "Ce lien ne sert pas à cela");
        }
        return token;
    }

    private static ForbiddenException badCredentials() {
        return new ForbiddenException("BAD_CREDENTIALS", "Adresse e-mail ou mot de passe incorrect");
    }
}
