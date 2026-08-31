package com.runtrack.auth.infrastructure.repository;

import com.runtrack.auth.infrastructure.repository.entity.RefreshTokenEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataRefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    List<RefreshTokenEntity> findAllByFamilyId(UUID familyId);

    List<RefreshTokenEntity> findAllByUserId(UUID userId);
}
