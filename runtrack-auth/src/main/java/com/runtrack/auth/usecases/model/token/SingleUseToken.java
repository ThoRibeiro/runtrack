package com.runtrack.auth.usecases.model.token;

import com.runtrack.shared.error.ConflictException;
import com.runtrack.shared.id.UserId;
import java.time.Instant;
import java.util.UUID;

/**
 * Un jeton envoyé par e-mail, valable une fois : confirmation d'adresse ou
 * réinitialisation de mot de passe.
 *
 * <p>Les deux usages partagent la même mécanique — secret opaque, empreinte en base,
 * expiration, consommation unique — et ne diffèrent que par leur durée de vie. Deux
 * classes jumelles auraient signifié corriger deux fois le même défaut.
 */
public final class SingleUseToken {

    private final UUID id;
    private final UserId userId;
    private final TokenPurpose purpose;
    private final String tokenHash;
    private final Instant expiresAt;

    private Instant consumedAt;

    private SingleUseToken(UUID id, UserId userId, TokenPurpose purpose, String tokenHash,
            Instant expiresAt, Instant consumedAt) {
        if (id == null || userId == null || purpose == null || tokenHash == null || expiresAt == null) {
            throw new IllegalArgumentException("Jeton à usage unique incomplet");
        }
        this.id = id;
        this.userId = userId;
        this.purpose = purpose;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.consumedAt = consumedAt;
    }

    public static SingleUseToken issue(UUID id, UserId userId, TokenPurpose purpose,
            String tokenHash, Instant issuedAt) {
        if (purpose == null || issuedAt == null) {
            throw new IllegalArgumentException("Jeton à usage unique incomplet");
        }
        return new SingleUseToken(id, userId, purpose, tokenHash, issuedAt.plus(purpose.lifetime()), null);
    }

    public static SingleUseToken rehydrate(UUID id, UserId userId, TokenPurpose purpose,
            String tokenHash, Instant expiresAt, Instant consumedAt) {
        return new SingleUseToken(id, userId, purpose, tokenHash, expiresAt, consumedAt);
    }

    public void consume(Instant at) {
        if (consumedAt != null) {
            throw new ConflictException("TOKEN_ALREADY_USED", "Ce lien a déjà servi");
        }
        if (!at.isBefore(expiresAt)) {
            throw new ConflictException("TOKEN_EXPIRED", "Ce lien a expiré");
        }
        this.consumedAt = at;
    }

    public boolean isUsableAt(Instant moment) {
        return consumedAt == null && moment.isBefore(expiresAt);
    }

    public UUID id() {
        return id;
    }

    public UserId userId() {
        return userId;
    }

    public TokenPurpose purpose() {
        return purpose;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant consumedAt() {
        return consumedAt;
    }
}
