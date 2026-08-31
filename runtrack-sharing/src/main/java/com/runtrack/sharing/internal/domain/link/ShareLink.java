package com.runtrack.sharing.internal.domain.link;

import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import java.time.Instant;
import java.util.Optional;

/**
 * Un lien de partage : ce qu'il ouvre, jusqu'à quand, et combien de fois il a servi.
 *
 * <p>Trois façons de ne plus fonctionner, et elles sont distinctes : révoqué par son auteur,
 * expiré de lui-même, ou pointant sur une course devenue invisible. Les deux premières vivent
 * ici ; la troisième appartient à {@code course}, qui tranche à chaque lecture — un lien n'est
 * pas un droit acquis sur une course dont le propriétaire a changé d'avis.
 */
public record ShareLink(
        ShareLinkId id,
        ActivityId activityId,
        UserId createdBy,
        String tokenHash,
        Instant createdAt,
        Optional<Instant> expiresAt,
        Optional<Instant> revokedAt,
        long viewCount) {

    public ShareLink {
        if (id == null || activityId == null || createdBy == null || tokenHash == null
                || createdAt == null || expiresAt == null || revokedAt == null) {
            throw new IllegalArgumentException("Lien de partage incomplet");
        }
        if (viewCount < 0) {
            throw new IllegalArgumentException("Compteur de vues négatif");
        }
    }

    public static ShareLink issued(ShareLinkId id, ActivityId activityId, UserId createdBy,
            ShareToken token, Instant createdAt, Optional<Instant> expiresAt) {

        return new ShareLink(id, activityId, createdBy, token.hash(), createdAt,
                expiresAt, Optional.empty(), 0);
    }

    public boolean isUsableAt(Instant moment) {
        return revokedAt.isEmpty() && expiresAt.map(moment::isBefore).orElse(true);
    }

    /** Révoquer un lien déjà révoqué ne déplace pas la date : c'est le premier geste qui compte. */
    public ShareLink revokedAt(Instant moment) {
        return revokedAt.isPresent() ? this : new ShareLink(
                id, activityId, createdBy, tokenHash, createdAt, expiresAt,
                Optional.of(moment), viewCount);
    }
}
