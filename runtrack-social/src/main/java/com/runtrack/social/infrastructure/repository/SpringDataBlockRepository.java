package com.runtrack.social.infrastructure.repository;

import com.runtrack.social.infrastructure.repository.entity.BlockEntity;
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
