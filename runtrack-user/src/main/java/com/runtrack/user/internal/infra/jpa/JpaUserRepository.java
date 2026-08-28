package com.runtrack.user.internal.infra.jpa;

import com.runtrack.shared.id.UserId;
import com.runtrack.user.internal.application.port.UserRepository;
import com.runtrack.user.internal.domain.profile.Email;
import com.runtrack.user.internal.domain.profile.Handle;
import com.runtrack.user.internal.domain.profile.User;
import com.runtrack.user.internal.infra.jpa.entity.UserEntity;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

/** L'implémentation du port. Traduit, et rien d'autre : aucune règle métier ne vit ici. */
@Repository
class JpaUserRepository implements UserRepository {

    private final SpringDataUserRepository entities;

    JpaUserRepository(SpringDataUserRepository entities) {
        this.entities = entities;
    }

    @Override
    public Optional<User> findById(UserId id) {
        return entities.findById(id.value()).map(UserEntityMapper::toDomain);
    }

    @Override
    public Optional<User> findByHandle(Handle handle) {
        return entities.findByHandle(handle.value()).map(UserEntityMapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return entities.findByEmail(email.value()).map(UserEntityMapper::toDomain);
    }

    @Override
    public boolean existsByHandle(Handle handle) {
        return entities.existsByHandle(handle.value());
    }

    @Override
    public boolean existsByEmail(Email email) {
        return entities.existsByEmail(email.value());
    }

    @Override
    public List<User> findAllById(Collection<UserId> ids) {
        return entities.findAllByIdIn(ids.stream().map(UserId::value).toList()).stream()
                .map(UserEntityMapper::toDomain)
                .toList();
    }

    @Override
    public List<User> search(String query, int limit) {
        String needle = "%" + query.toLowerCase(Locale.ROOT) + "%";
        return entities.search(needle, Limit.of(limit)).stream().map(UserEntityMapper::toDomain).toList();
    }

    /**
     * Recharge la ligne existante et la met à jour sur place, plutôt que de persister une
     * entité neuve : c'est ce qui laisse le verrou optimiste de JPA faire son travail au
     * lieu d'écraser une écriture concurrente.
     */
    @Override
    public User save(User user) {
        UserEntity incoming = UserEntityMapper.toEntity(user);
        UserEntity persisted = entities.findById(user.id().value())
                .map(existing -> {
                    existing.refreshFrom(incoming);
                    return existing;
                })
                .orElse(incoming);
        return UserEntityMapper.toDomain(entities.save(persisted));
    }
}
