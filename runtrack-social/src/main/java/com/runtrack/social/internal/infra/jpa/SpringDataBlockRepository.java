package com.runtrack.social.internal.infra.jpa;

import com.runtrack.social.internal.infra.jpa.entity.BlockEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataBlockRepository extends JpaRepository<BlockEntity, UUID> {

    Optional<BlockEntity> findByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    List<BlockEntity> findAllByBlockerId(UUID blockerId);

    void deleteByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);
}
