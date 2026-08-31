package com.runtrack.social.infrastructure.repository;

import com.runtrack.social.infrastructure.repository.entity.FollowEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataFollowRepository extends JpaRepository<FollowEntity, UUID> {

    Optional<FollowEntity> findByFollowerIdAndFolloweeId(UUID followerId, UUID followeeId);

    @Query("select f.followerId from FollowEntity f where f.followeeId = :id and f.status = 'ACCEPTED'")
    List<UUID> acceptedFollowerIds(@Param("id") UUID followeeId);

    @Query("select f.followeeId from FollowEntity f where f.followerId = :id and f.status = 'ACCEPTED'")
    List<UUID> acceptedFolloweeIds(@Param("id") UUID followerId);

    List<FollowEntity> findAllByFolloweeIdAndStatusOrderByRequestedAtDesc(UUID followeeId, String status);

    /** Une seule instruction pour les deux sens : un blocage rompt la relation, pas un côté. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from FollowEntity f
            where (f.followerId = :one and f.followeeId = :other)
               or (f.followerId = :other and f.followeeId = :one)
            """)
    void deleteBetween(@Param("one") UUID one, @Param("other") UUID other);
}
