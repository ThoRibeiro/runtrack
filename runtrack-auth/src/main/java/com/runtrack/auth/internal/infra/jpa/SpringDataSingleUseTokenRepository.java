package com.runtrack.auth.internal.infra.jpa;

import com.runtrack.auth.internal.infra.jpa.entity.SingleUseTokenEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataSingleUseTokenRepository extends JpaRepository<SingleUseTokenEntity, UUID> {

    Optional<SingleUseTokenEntity> findByTokenHash(String tokenHash);

    List<SingleUseTokenEntity> findAllByUserIdAndPurposeAndConsumedAtIsNull(UUID userId, String purpose);
}
