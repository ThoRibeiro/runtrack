package com.runtrack.user.internal.application.port;

import com.runtrack.shared.id.UserId;
import com.runtrack.user.internal.domain.profile.Email;
import com.runtrack.user.internal.domain.profile.Handle;
import com.runtrack.user.internal.domain.profile.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Le port de persistance des profils. Déclaré ici, implémenté dans {@code infra} : aucun
 * type JPA n'apparaît dans ces signatures, ce que vérifie ArchUnit.
 */
public interface UserRepository {

    Optional<User> findById(UserId id);

    Optional<User> findByHandle(Handle handle);

    Optional<User> findByEmail(Email email);

    boolean existsByHandle(Handle handle);

    boolean existsByEmail(Email email);

    /** Plusieurs profils en une requête : c'est ce qui évite le N+1 sur le fil et les listes. */
    List<User> findAllById(Collection<UserId> ids);

    /** Recherche sur l'identifiant public et le nom affiché, comptes supprimés exclus. */
    List<User> search(String query, int limit);

    User save(User user);
}
