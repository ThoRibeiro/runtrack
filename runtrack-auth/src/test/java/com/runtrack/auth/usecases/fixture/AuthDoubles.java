package com.runtrack.auth.usecases.fixture;

import com.runtrack.auth.usecases.port.AccessTokenIssuer;
import com.runtrack.auth.usecases.port.AuthMailer;
import com.runtrack.auth.usecases.port.CredentialsRepository;
import com.runtrack.auth.usecases.port.PasswordHasher;
import com.runtrack.auth.usecases.port.RefreshTokenRepository;
import com.runtrack.auth.usecases.port.SingleUseTokenRepository;
import com.runtrack.auth.usecases.model.credential.Credentials;
import com.runtrack.auth.usecases.model.credential.Password;
import com.runtrack.auth.usecases.model.credential.PasswordHash;
import com.runtrack.auth.usecases.model.token.RefreshToken;
import com.runtrack.auth.usecases.model.token.SingleUseToken;
import com.runtrack.auth.usecases.model.token.TokenPurpose;
import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.id.UserId;
import com.runtrack.user.NewUser;
import com.runtrack.user.RunnerMass;
import com.runtrack.user.UserApi;
import com.runtrack.user.UserSummary;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Doubles en mémoire des ports d'{@code auth}.
 *
 * <p>Le hacheur est volontairement trivial — Argon2 est lent par construction, et le faire
 * tourner dans un test unitaire n'apprendrait rien sur le cas d'usage. Sa vraie
 * implémentation est couverte par un test d'intégration.
 */
public final class AuthDoubles {

    private AuthDoubles() {
    }

    public static final class Users implements UserApi {

        private final Map<String, UserId> byEmail = new LinkedHashMap<>();
        public final List<UserId> confirmed = new ArrayList<>();

        @Override
        public UserId register(NewUser newUser) {
            UserId id = new UserId(UUID.randomUUID());
            byEmail.put(newUser.email(), id);
            return id;
        }

        @Override
        public void confirmEmail(UserId id) {
            confirmed.add(id);
        }

        @Override
        public Optional<UserId> idOfEmail(String email) {
            return Optional.ofNullable(byEmail.get(email));
        }

        @Override
        public boolean exists(UserId id) {
            return byEmail.containsValue(id);
        }

        @Override
        public Optional<UserSummary> summary(UserId id) {
            return Optional.empty();
        }

        @Override
        public Map<UserId, UserSummary> summaries(Collection<UserId> ids) {
            return Map.of();
        }

        @Override
        public Optional<AudienceScope> accountScope(UserId id) {
            return Optional.empty();
        }

        @Override
        public Optional<RunnerMass> massOf(UserId id) {
            return Optional.empty();
        }
    }

    public static final class Credential implements CredentialsRepository {

        private final Map<UserId, Credentials> stored = new LinkedHashMap<>();

        @Override
        public Optional<Credentials> findByUserId(UserId userId) {
            return Optional.ofNullable(stored.get(userId));
        }

        @Override
        public Credentials save(Credentials credentials) {
            stored.put(credentials.userId(), credentials);
            return credentials;
        }
    }

    public static final class RefreshTokens implements RefreshTokenRepository {

        private final Map<UUID, RefreshToken> stored = new LinkedHashMap<>();

        @Override
        public Optional<RefreshToken> findByHash(String tokenHash) {
            return stored.values().stream().filter(t -> t.tokenHash().equals(tokenHash)).findFirst();
        }

        @Override
        public RefreshToken save(RefreshToken token) {
            stored.put(token.id(), token);
            return token;
        }

        @Override
        public void revokeFamily(UUID familyId) {
            stored.values().stream().filter(t -> t.familyId().equals(familyId)).forEach(RefreshToken::revoke);
        }

        @Override
        public void revokeAllOf(UserId userId) {
            stored.values().stream().filter(t -> t.userId().equals(userId)).forEach(RefreshToken::revoke);
        }

        public List<RefreshToken> all() {
            return List.copyOf(stored.values());
        }
    }

    public static final class SingleUseTokens implements SingleUseTokenRepository {

        private final Map<UUID, SingleUseToken> stored = new LinkedHashMap<>();
        private final Clock clock;

        public SingleUseTokens(Clock clock) {
            this.clock = clock;
        }

        @Override
        public Optional<SingleUseToken> findByHash(String tokenHash) {
            return stored.values().stream().filter(t -> t.tokenHash().equals(tokenHash)).findFirst();
        }

        @Override
        public SingleUseToken save(SingleUseToken token) {
            stored.put(token.id(), token);
            return token;
        }

        @Override
        public void consumeAllOf(UserId userId, TokenPurpose purpose) {
            stored.values().stream()
                    .filter(t -> t.userId().equals(userId) && t.purpose() == purpose)
                    .filter(t -> t.isUsableAt(clock.instant()))
                    .forEach(t -> t.consume(clock.instant()));
        }
    }

    /** Hachage réversible : le test porte sur le cas d'usage, pas sur la cryptographie. */
    public static final class Hasher implements PasswordHasher {

        @Override
        public PasswordHash hash(Password password) {
            return new PasswordHash("hashed:" + password.value());
        }

        @Override
        public boolean matches(Password candidate, PasswordHash hash) {
            return hash.value().equals("hashed:" + candidate.value());
        }
    }

    public static final class AccessTokens implements AccessTokenIssuer {

        @Override
        public String issueFor(UserId userId) {
            return "access-for-" + userId;
        }

        @Override
        public Duration lifetime() {
            return Duration.ofMinutes(15);
        }
    }

    public record SentMail(String address, String secret, TokenPurpose purpose) {
    }

    public static final class Mailer implements AuthMailer {

        public final List<SentMail> sent = new ArrayList<>();

        @Override
        public void sendEmailVerification(String emailAddress, String secret) {
            sent.add(new SentMail(emailAddress, secret, TokenPurpose.EMAIL_VERIFICATION));
        }

        @Override
        public void sendPasswordReset(String emailAddress, String secret) {
            sent.add(new SentMail(emailAddress, secret, TokenPurpose.PASSWORD_RESET));
        }
    }

    public static RandomGenerator seededRandom() {
        return new java.util.Random(1_234);
    }
}
