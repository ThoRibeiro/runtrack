package com.runtrack.user.internal.application.fixture;

import com.runtrack.shared.id.UserId;
import com.runtrack.user.internal.application.port.UserRepository;
import com.runtrack.user.internal.domain.profile.AccountStatus;
import com.runtrack.user.internal.domain.profile.Email;
import com.runtrack.user.internal.domain.profile.Handle;
import com.runtrack.user.internal.domain.profile.User;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Double en mémoire du port de persistance.
 *
 * <p>Un vrai double plutôt qu'un mock : il applique les mêmes règles de recherche et
 * d'unicité que l'implémentation JPA, donc un test qui passe ici dit quelque chose du
 * comportement, pas seulement des appels qui ont eu lieu.
 */
public final class InMemoryUserRepository implements UserRepository {

    private final Map<UserId, User> stored = new LinkedHashMap<>();

    @Override
    public Optional<User> findById(UserId id) {
        return Optional.ofNullable(stored.get(id));
    }

    @Override
    public Optional<User> findByHandle(Handle handle) {
        return stored.values().stream().filter(user -> user.handle().equals(handle)).findFirst();
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return stored.values().stream().filter(user -> user.email().equals(email)).findFirst();
    }

    @Override
    public boolean existsByHandle(Handle handle) {
        return findByHandle(handle).isPresent();
    }

    @Override
    public boolean existsByEmail(Email email) {
        return findByEmail(email).isPresent();
    }

    @Override
    public List<User> findAllById(Collection<UserId> ids) {
        var found = new ArrayList<User>();
        for (UserId id : ids) {
            findById(id).ifPresent(found::add);
        }
        return List.copyOf(found);
    }

    @Override
    public List<User> search(String query, int limit) {
        String needle = query.toLowerCase(Locale.ROOT);
        return stored.values().stream()
                .filter(user -> user.status() != AccountStatus.DELETED)
                .filter(user -> user.handle().value().contains(needle)
                        || user.displayName().toLowerCase(Locale.ROOT).contains(needle))
                .limit(limit)
                .toList();
    }

    @Override
    public User save(User user) {
        stored.put(user.id(), user);
        return user;
    }

    public int size() {
        return stored.size();
    }
}
