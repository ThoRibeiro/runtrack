package com.runtrack.auth.infrastructure.repository;

import com.runtrack.auth.usecases.port.RefreshTokenRepository;
import com.runtrack.auth.usecases.model.token.RefreshToken;
import com.runtrack.auth.infrastructure.repository.entity.RefreshTokenEntity;
import com.runtrack.shared.id.UserId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class JpaRefreshTokenRepository implements RefreshTokenRepository {

    private final SpringDataRefreshTokenRepository entities;

    JpaRefreshTokenRepository(SpringDataRefreshTokenRepository entities) {
        this.entities = entities;
    }

    @Override
    public Optional<RefreshToken> findByHash(String tokenHash) {
        return entities.findByTokenHash(tokenHash).map(JpaRefreshTokenRepository::toDomain);
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        var incoming = new RefreshTokenEntity(token.id(), token.userId().value(), token.familyId(),
                token.tokenHash(), token.issuedAt(), token.expiresAt(), token.consumedAt(), token.isRevoked());
        entities.save(entities.findById(token.id())
                .map(existing -> {
                    existing.refreshFrom(incoming);
                    return existing;
                })
                .orElse(incoming));
        return token;
    }

    /** Une seule requête pour toute la chaîne : c'est le chemin chaud de la détection de rejeu. */
    @Override
    public void revokeFamily(UUID familyId) {
        revokeAll(entities.findAllByFamilyId(familyId));
    }

    @Override
    public void revokeAllOf(UserId userId) {
        revokeAll(entities.findAllByUserId(userId.value()));
    }

    private void revokeAll(List<RefreshTokenEntity> tokens) {
        tokens.forEach(RefreshTokenEntity::revoke);
        entities.saveAll(tokens);
    }

    private static RefreshToken toDomain(RefreshTokenEntity entity) {
        return RefreshToken.rehydrate(entity.getId(), new UserId(entity.getUserId()), entity.getFamilyId(),
                entity.getTokenHash(), entity.getIssuedAt(), entity.getExpiresAt(),
                entity.getConsumedAt(), entity.isRevoked());
    }
}
