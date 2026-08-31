package com.runtrack.course.infrastructure.repository;

import com.runtrack.course.infrastructure.repository.entity.IdempotencyKeyEntity;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataIdempotencyRepository
        extends JpaRepository<IdempotencyKeyEntity, IdempotencyKeyEntity.Id> {

    /**
     * Suppression en masse, et non un {@code deleteBy…} dérivé : celui-ci chargerait chaque
     * ligne en mémoire avant de l'effacer, alors qu'on en efface une journée entière.
     */
    @Modifying
    @Query("delete from IdempotencyKeyEntity key where key.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
