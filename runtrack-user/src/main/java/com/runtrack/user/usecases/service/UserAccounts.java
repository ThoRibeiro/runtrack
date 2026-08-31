package com.runtrack.user.usecases.service;

import com.runtrack.shared.error.ConflictException;
import com.runtrack.shared.error.NotFoundException;
import com.runtrack.shared.id.UserId;
import com.runtrack.user.event.UserDeleted;
import com.runtrack.user.event.UserProfileUpdated;
import com.runtrack.user.event.UserRegistered;
import com.runtrack.user.usecases.port.UserRepository;
import com.runtrack.user.usecases.model.profile.Email;
import com.runtrack.user.usecases.model.profile.Handle;
import com.runtrack.user.usecases.model.profile.Physiology;
import com.runtrack.user.usecases.model.profile.User;
import com.runtrack.shared.access.AudienceScope;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.random.RandomGenerator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Les cas d'usage du profil.
 *
 * <p>L'horloge et le générateur aléatoire sont injectés : c'est ce qui rend l'inscription
 * et l'anonymisation reproductibles en test, et ce qui permet à ArchUnit d'interdire tout
 * {@code Instant.now()} en dur.
 */
@Service
public class UserAccounts {

    private static final int ANONYMOUS_SUFFIX_BYTES = 4;
    private static final int MAX_SEARCH_RESULTS = 20;

    private final UserRepository users;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final RandomGenerator random;

    public UserAccounts(UserRepository users, ApplicationEventPublisher events,
            Clock clock, RandomGenerator random) {
        this.users = users;
        this.events = events;
        this.clock = clock;
        this.random = random;
    }

    /**
     * Crée le profil. Appelé par {@code auth} à l'inscription — c'est bien {@code user} qui
     * publie {@code UserRegistered}, sans quoi les deux modules formeraient un cycle.
     */
    @Transactional
    public UserId register(Handle handle, Email email, String displayName) {
        if (users.existsByHandle(handle)) {
            throw new ConflictException("HANDLE_TAKEN", "Cet identifiant public est déjà pris");
        }
        if (users.existsByEmail(email)) {
            throw new ConflictException("EMAIL_TAKEN", "Cette adresse e-mail est déjà utilisée");
        }

        var now = clock.instant();
        User user = users.save(User.register(UserId.generate(clock, random), handle, email, displayName, now));
        events.publishEvent(new UserRegistered(user.id(), user.handle().value(), now));
        return user.id();
    }

    @Transactional
    public void verifyEmail(UserId id) {
        User user = require(id);
        user.verifyEmail();
        users.save(user);
    }

    @Transactional
    public void updateProfile(UserId id, String displayName, String bio, String avatarUrl) {
        User user = require(id);
        user.updateProfile(displayName, bio, avatarUrl);
        publishUpdate(users.save(user));
    }

    @Transactional
    public void changeHandle(UserId id, Handle handle) {
        User user = require(id);
        if (!user.handle().equals(handle) && users.existsByHandle(handle)) {
            throw new ConflictException("HANDLE_TAKEN", "Cet identifiant public est déjà pris");
        }
        user.changeHandle(handle);
        publishUpdate(users.save(user));
    }

    @Transactional
    public void changeAccountScope(UserId id, AudienceScope scope) {
        User user = require(id);
        user.changeAccountScope(scope);
        publishUpdate(users.save(user));
    }

    @Transactional
    public void recordPhysiology(UserId id, Physiology physiology) {
        User user = require(id);
        user.recordPhysiology(physiology);
        users.save(user);
    }

    /**
     * Suppression au sens du RGPD : anonymisation. Le compte reste, vidé de tout ce qui
     * identifie la personne, parce que ses courses et ses commentaires le référencent.
     */
    @Transactional
    public void delete(UserId id) {
        User user = require(id);
        user.anonymize(newAnonymousSuffix());
        users.save(user);
        events.publishEvent(new UserDeleted(user.id(), clock.instant()));
    }

    @Transactional(readOnly = true)
    public User byId(UserId id) {
        return require(id);
    }

    @Transactional(readOnly = true)
    public User byHandle(Handle handle) {
        return users.findByHandle(handle)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Profil introuvable : " + handle.value()));
    }

    @Transactional(readOnly = true)
    public List<User> search(String query) {
        return query == null || query.isBlank() ? List.of() : users.search(query.strip(), MAX_SEARCH_RESULTS);
    }

    private User require(UserId id) {
        return users.findById(id)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Profil introuvable : " + id));
    }

    private void publishUpdate(User user) {
        events.publishEvent(new UserProfileUpdated(user.id(), user.handle().value(), clock.instant()));
    }

    private String newAnonymousSuffix() {
        var bytes = new byte[ANONYMOUS_SUFFIX_BYTES];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
