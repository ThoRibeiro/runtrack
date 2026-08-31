package com.runtrack.auth.infrastructure.repository;

import com.runtrack.auth.usecases.port.SingleUseTokenRepository;
import com.runtrack.auth.usecases.model.token.SingleUseToken;
import com.runtrack.auth.usecases.model.token.TokenPurpose;
import com.runtrack.auth.infrastructure.repository.entity.SingleUseTokenEntity;
import com.runtrack.shared.id.UserId;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaSingleUseTokenRepository implements SingleUseTokenRepository {

    private final SpringDataSingleUseTokenRepository entities;
    private final Clock clock;

    JpaSingleUseTokenRepository(SpringDataSingleUseTokenRepository entities, Clock clock) {
        this.entities = entities;
        this.clock = clock;
    }

    @Override
    public Optional<SingleUseToken> findByHash(String tokenHash) {
        return entities.findByTokenHash(tokenHash).map(JpaSingleUseTokenRepository::toDomain);
    }

    @Override
    public SingleUseToken save(SingleUseToken token) {
        var incoming = new SingleUseTokenEntity(token.id(), token.userId().value(), token.purpose().name(),
                token.tokenHash(), token.expiresAt(), token.consumedAt());
        entities.save(entities.findById(token.id())
                .map(existing -> {
                    existing.refreshFrom(incoming);
                    return existing;
                })
                .orElse(incoming));
        return token;
    }

    /** Émettre un nouveau lien périme ceux qui circulent encore. */
    @Override
    public void consumeAllOf(UserId userId, TokenPurpose purpose) {
        var now = clock.instant();
        entities.findAllByUserIdAndPurposeAndConsumedAtIsNull(userId.value(), purpose.name()).stream()
                .map(entity -> new SingleUseTokenEntity(entity.getId(), entity.getUserId(), entity.getPurpose(),
                        entity.getTokenHash(), entity.getExpiresAt(), now))
                .forEach(entities::save);
    }

    private static SingleUseToken toDomain(SingleUseTokenEntity entity) {
        return SingleUseToken.rehydrate(entity.getId(), new UserId(entity.getUserId()),
                TokenPurpose.valueOf(entity.getPurpose()), entity.getTokenHash(),
                entity.getExpiresAt(), entity.getConsumedAt());
    }
}
