package com.runtrack.auth.internal.domain.token;

import com.runtrack.shared.error.ForbiddenException;
import com.runtrack.shared.id.UserId;
import java.time.Instant;
import java.util.UUID;

/**
 * Un jeton de rafraîchissement, rotatif et appartenant à une <em>famille</em>.
 *
 * <p>Chaque rafraîchissement consomme le jeton présenté et en émet un nouveau dans la même
 * famille. Un jeton consommé qui se représente signifie qu'il a été volé : ou bien c'est
 * le voleur qui l'utilise, ou bien c'est la victime après le voleur — dans les deux cas
 * quelqu'un détient une copie. La seule réponse sûre est de révoquer <em>toute la
 * famille</em>, ce qui déconnecte l'attaquant et l'utilisateur, qui se reconnectera.
 *
 * <p>Se contenter de refuser le jeton rejoué laisserait la chaîne du voleur intacte.
 */
public final class RefreshToken {

    private final UUID id;
    private final UserId userId;
    private final UUID familyId;
    private final String tokenHash;
    private final Instant issuedAt;
    private final Instant expiresAt;

    private Instant consumedAt;
    private boolean revoked;

    private RefreshToken(UUID id, UserId userId, UUID familyId, String tokenHash,
            Instant issuedAt, Instant expiresAt, Instant consumedAt, boolean revoked) {
        if (id == null || userId == null || familyId == null || tokenHash == null
                || issuedAt == null || expiresAt == null) {
            throw new IllegalArgumentException("Jeton de rafraîchissement incomplet");
        }
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("Un jeton ne peut pas expirer avant d'être émis");
        }
        this.id = id;
        this.userId = userId;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.consumedAt = consumedAt;
        this.revoked = revoked;
    }

    /** Ouvre une nouvelle famille : c'est ce que fait une connexion. */
    public static RefreshToken openFamily(UUID id, UserId userId, String tokenHash,
            Instant issuedAt, Instant expiresAt) {
        return new RefreshToken(id, userId, UUID.randomUUID(), tokenHash, issuedAt, expiresAt, null, false);
    }

    /** Prolonge une famille existante : c'est ce que fait un rafraîchissement. */
    public RefreshToken succeededBy(UUID id, String tokenHash, Instant issuedAt, Instant expiresAt) {
        return new RefreshToken(id, userId, familyId, tokenHash, issuedAt, expiresAt, null, false);
    }

    public static RefreshToken rehydrate(UUID id, UserId userId, UUID familyId, String tokenHash,
            Instant issuedAt, Instant expiresAt, Instant consumedAt, boolean revoked) {
        return new RefreshToken(id, userId, familyId, tokenHash, issuedAt, expiresAt, consumedAt, revoked);
    }

    /**
     * Marque le jeton comme utilisé.
     *
     * <p>Tous les refus sont des {@link ForbiddenException} : révoqué, rejoué ou expiré,
     * c'est la même chose vue du client — la session ne vaut plus, il faut se reconnecter.
     * Rendre 409 dans un cas et 403 dans l'autre l'obligerait à traiter deux chemins pour
     * une seule situation. Le code métier, lui, reste distinct.
     *
     * @throws ForbiddenException s'il a déjà servi, s'il a été révoqué ou s'il a expiré
     */
    public void consume(Instant at) {
        if (revoked) {
            throw new ForbiddenException("REFRESH_TOKEN_REVOKED", "Session close, reconnexion nécessaire");
        }
        if (consumedAt != null) {
            throw new ForbiddenException("REFRESH_TOKEN_REUSED", "Ce jeton a déjà servi");
        }
        if (!at.isBefore(expiresAt)) {
            throw new ForbiddenException("REFRESH_TOKEN_EXPIRED", "Session expirée, reconnexion nécessaire");
        }
        this.consumedAt = at;
    }

    public void revoke() {
        this.revoked = true;
    }

    public boolean isUsableAt(Instant moment) {
        return !revoked && consumedAt == null && moment.isBefore(expiresAt);
    }

    public boolean wasConsumed() {
        return consumedAt != null;
    }

    public UUID id() {
        return id;
    }

    public UserId userId() {
        return userId;
    }

    public UUID familyId() {
        return familyId;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant consumedAt() {
        return consumedAt;
    }

    public boolean isRevoked() {
        return revoked;
    }
}
