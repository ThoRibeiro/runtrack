package com.runtrack.social.internal.infra.jpa;

import com.runtrack.shared.id.UserId;
import com.runtrack.social.internal.application.port.BlockRepository;
import com.runtrack.social.internal.domain.graph.Block;
import com.runtrack.social.internal.infra.jpa.entity.BlockEntity;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaBlockRepository implements BlockRepository {

    private final SpringDataBlockRepository entities;

    JpaBlockRepository(SpringDataBlockRepository entities) {
        this.entities = entities;
    }

    @Override
    public Optional<Block> findBetween(UserId blockerId, UserId blockedId) {
        return entities.findByBlockerIdAndBlockedId(blockerId.value(), blockedId.value())
                .map(JpaBlockRepository::toDomain);
    }

    @Override
    public boolean existsEitherWay(UserId one, UserId other) {
        return entities.existsByBlockerIdAndBlockedId(one.value(), other.value())
                || entities.existsByBlockerIdAndBlockedId(other.value(), one.value());
    }

    @Override
    public Set<UserId> blockedBy(UserId blockerId) {
        var result = new LinkedHashSet<UserId>();
        entities.findAllByBlockerId(blockerId.value())
                .forEach(entity -> result.add(new UserId(entity.getBlockedId())));
        return result;
    }

    @Override
    public Block save(Block block) {
        entities.save(new BlockEntity(block.id(), block.blockerId().value(),
                block.blockedId().value(), block.at()));
        return block;
    }

    @Override
    @Transactional
    public void delete(UserId blockerId, UserId blockedId) {
        entities.deleteByBlockerIdAndBlockedId(blockerId.value(), blockedId.value());
    }

    private static Block toDomain(BlockEntity entity) {
        return new Block(entity.getId(), new UserId(entity.getBlockerId()),
                new UserId(entity.getBlockedId()), entity.getAt());
    }
}
