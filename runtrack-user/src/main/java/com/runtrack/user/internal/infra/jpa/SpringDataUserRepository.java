package com.runtrack.user.internal.infra.jpa;

import com.runtrack.user.internal.infra.jpa.entity.UserEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * L'interface Spring Data. Confinée à {@code infra} : aucun cas d'usage ne la voit, ils ne
 * connaissent que le port {@code UserRepository}.
 */
interface SpringDataUserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByHandle(String handle);

    Optional<UserEntity> findByEmail(String email);

    boolean existsByHandle(String handle);

    boolean existsByEmail(String email);

    List<UserEntity> findAllByIdIn(Collection<UUID> ids);

    /** Comptes supprimés exclus : leur identifiant public anonymisé n'a rien à faire ici. */
    @Query("""
            select u from UserEntity u
            where u.status <> 'DELETED'
              and (lower(u.handle) like :needle or lower(u.displayName) like :needle)
            order by u.handle
            """)
    List<UserEntity> search(@Param("needle") String needle, Limit limit);
}
