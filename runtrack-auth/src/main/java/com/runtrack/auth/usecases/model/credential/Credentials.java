package com.runtrack.auth.usecases.model.credential;

import com.runtrack.shared.id.UserId;
import java.time.Instant;

/** Le moyen de connexion d'un compte : une empreinte, et la date de son dernier changement. */
public final class Credentials {

    private final UserId userId;
    private PasswordHash passwordHash;
    private Instant passwordChangedAt;

    private Credentials(UserId userId, PasswordHash passwordHash, Instant passwordChangedAt) {
        if (userId == null || passwordHash == null || passwordChangedAt == null) {
            throw new IllegalArgumentException("Identifiants incomplets");
        }
        this.userId = userId;
        this.passwordHash = passwordHash;
        this.passwordChangedAt = passwordChangedAt;
    }

    public static Credentials create(UserId userId, PasswordHash hash, Instant at) {
        return new Credentials(userId, hash, at);
    }

    public static Credentials rehydrate(UserId userId, PasswordHash hash, Instant passwordChangedAt) {
        return new Credentials(userId, hash, passwordChangedAt);
    }

    public void changePassword(PasswordHash newHash, Instant at) {
        this.passwordHash = newHash;
        this.passwordChangedAt = at;
    }

    public UserId userId() {
        return userId;
    }

    public PasswordHash passwordHash() {
        return passwordHash;
    }

    public Instant passwordChangedAt() {
        return passwordChangedAt;
    }
}
