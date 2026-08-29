package com.runtrack.course.internal.infra.jpa;

import com.runtrack.course.internal.infra.jpa.entity.ActivityEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataActivityRepository extends JpaRepository<ActivityEntity, UUID> {

    List<ActivityEntity> findAllByIdIn(Collection<UUID> ids);

    /**
     * Pagination par curseur, jamais par offset : sur un fil qui bouge, un {@code OFFSET}
     * saute ou répète des lignes dès qu'une course s'insère pendant la lecture.
     *
     * <p>Deux requêtes plutôt qu'un {@code :before is null} : PostgreSQL ne sait pas
     * inférer le type d'un paramètre qui n'apparaît qu'à côté d'un test de nullité, et la
     * requête échoue à l'exécution.
     */
    List<ActivityEntity> findAllByOwnerIdOrderByStartedAtDesc(UUID ownerId, Limit limit);

    List<ActivityEntity> findAllByOwnerIdAndStartedAtLessThanOrderByStartedAtDesc(
            UUID ownerId, Instant before, Limit limit);

    List<ActivityEntity> findAllByOwnerIdInAndStatus(Collection<UUID> ownerIds, String status);
}
