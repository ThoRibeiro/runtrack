package com.runtrack.user.infrastructure.repository;

import com.runtrack.user.infrastructure.repository.entity.AvatarImageEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Confinée à {@code infrastructure}, comme celle des comptes : le port suffit au domaine. */
interface SpringDataAvatarRepository extends JpaRepository<AvatarImageEntity, UUID> {

    void deleteAllByUserId(UUID userId);
}
