package com.runtrack.user.usecases.service;

import com.runtrack.shared.error.ConflictException;
import com.runtrack.shared.error.NotFoundException;
import com.runtrack.shared.id.UserId;
import com.runtrack.user.FederatedProfile;
import com.runtrack.user.event.UserDeleted;
import com.runtrack.user.event.UserProfileUpdated;
import com.runtrack.user.event.UserRegistered;
import com.runtrack.user.usecases.port.AvatarStore;
import com.runtrack.user.usecases.port.UserRepository;
import com.runtrack.user.usecases.model.profile.Email;
import com.runtrack.user.usecases.model.profile.Handle;
import com.runtrack.user.usecases.model.profile.Physiology;
import com.runtrack.user.usecases.model.profile.StoredImage;
import com.runtrack.user.usecases.model.profile.User;
import com.runtrack.shared.access.AudienceScope;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
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

    /** Le pseudo d'attente d'un compte fédéré, avant que la personne n'en choisisse un. */
    private static final String PROVISIONAL_HANDLE_PREFIX = "runner-";

    /** Longueurs successives d'identifiant essayées : 8 suffisent, 23 tiennent dans les 30. */
    private static final int[] PROVISIONAL_HANDLE_LENGTHS = {8, 12, 16, 23};
    private static final int MAX_SEARCH_RESULTS = 20;

    /**
     * Deux mébioctets. Une photo de profil s'affiche en 96 points de côté : au-delà, ce
     * n'est plus une photo qu'on téléverse, c'est une image d'appareil photo qu'on n'a pas
     * redimensionnée — et c'est le client qui doit s'en charger, pas la base.
     */
    private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;

    /** Ce qu'un navigateur et un téléphone savent tous les deux afficher. */
    private static final Set<String> ACCEPTED_IMAGE_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");

    private final UserRepository users;
    private final AvatarStore avatars;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final RandomGenerator random;

    public UserAccounts(UserRepository users, AvatarStore avatars, ApplicationEventPublisher events,
            Clock clock, RandomGenerator random) {
        this.users = users;
        this.avatars = avatars;
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

    /**
     * Ouvre le profil d'une identité fédérée, si elle n'en a pas déjà un.
     *
     * <p><b>L'identifiant n'est pas tiré ici.</b> Il vient du {@code sub} du jeton : garder une
     * table de correspondance entre l'identité du fournisseur et la nôtre coûterait une lecture
     * par requête, un cache, et l'invalidation de ce cache — pour ne rien apporter que le
     * {@code sub} ne dise déjà.
     *
     * <p>Le pseudo est <b>provisoire</b> et dérivé de l'identifiant. Le demander au fournisseur
     * reviendrait à lui confier une règle du domaine ; le demander à la personne au milieu d'une
     * redirection OIDC n'est pas possible. Elle le choisit ensuite, par
     * {@code PUT /user/v1/me/handle}.
     *
     * <p>Une adresse déjà portée par un autre profil <b>arrête tout</b>. Rattacher
     * silencieusement une identité fédérée à un compte existant sur la seule foi de l'adresse
     * est le scénario classique de prise de contrôle : il suffit qu'un fournisseur laisse
     * déclarer une adresse non vérifiée.
     */
    @Transactional
    public boolean provisionFederated(UserId id, FederatedProfile profile) {
        if (users.findById(id).isPresent()) {
            return false;
        }
        Email email = new Email(profile.email());
        if (users.existsByEmail(email)) {
            throw new ConflictException("EMAIL_TAKEN",
                    "Cette adresse e-mail appartient déjà à un autre compte");
        }

        var now = clock.instant();
        User user = User.register(id, provisionalHandle(id), email, profile.displayName(), now);
        if (profile.emailVerified()) {
            // Le fournisseur l'a vérifiée : refaire le tour par un courriel maison demanderait
            // à la personne de prouver deux fois la même chose.
            user.verifyEmail(now);
        }
        users.save(user);
        events.publishEvent(new UserRegistered(user.id(), user.handle().value(), now));
        return true;
    }

    /**
     * Un pseudo libre, dérivé de l'identifiant.
     *
     * <p>On rallonge tant qu'il est pris plutôt que de tirer au hasard : deux tirages peuvent
     * entrer en collision indéfiniment, l'identifiant complet est unique par construction.
     */
    private Handle provisionalHandle(UserId id) {
        String hex = id.value().toString().replace("-", "");
        for (int length : PROVISIONAL_HANDLE_LENGTHS) {
            Handle candidate = new Handle(PROVISIONAL_HANDLE_PREFIX + hex.substring(0, length));
            if (!users.existsByHandle(candidate)) {
                return candidate;
            }
        }
        throw new ConflictException("HANDLE_TAKEN",
                "Impossible de dériver un identifiant public libre pour ce compte");
    }

    @Transactional
    public void verifyEmail(UserId id) {
        User user = require(id);
        user.verifyEmail(clock.instant());
        users.save(user);
    }

    @Transactional
    public void updateProfile(UserId id, String displayName, String bio, String avatarUrl) {
        User user = require(id);
        user.updateProfile(displayName, bio, avatarUrl, clock.instant());
        publishUpdate(users.save(user));
    }

    @Transactional
    public void changeAvatar(UserId id, String avatarUrl) {
        User user = require(id);
        user.changeAvatar(avatarUrl, clock.instant());
        publishUpdate(users.save(user));
    }

    /**
     * Téléverse une photo et la rattache au compte.
     *
     * <p>C'est le service qui fabrique l'adresse, à partir du modèle que lui donne la
     * couche REST : le domaine sait qu'une photo a une adresse, pas qu'un serveur HTTP
     * écoute sur tel hôte.
     *
     * <p>Le type et la taille sont vérifiés ici et pas seulement au bord : une limite qui
     * ne vit que dans le contrôleur est une limite qu'un second appelant contourne.
     */
    @Transactional
    public String uploadAvatar(UserId id, String contentType, byte[] bytes,
            UnaryOperator<String> addressOf) {
        User user = require(id);

        String normalised = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (!ACCEPTED_IMAGE_TYPES.contains(normalised)) {
            // Comme partout ailleurs : une valeur refusée par le domaine est un
            // IllegalArgumentException, que l'advice traduit en 400 INVALID_VALUE.
            throw new IllegalArgumentException("Une photo de profil doit être une image JPEG, PNG ou WebP");
        }
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Cette image est vide");
        }
        if (bytes.length > MAX_AVATAR_BYTES) {
            throw new IllegalArgumentException("Une photo de profil ne peut pas dépasser 2 Mo");
        }

        String imageId = avatars.replace(id, normalised, bytes);
        String address = addressOf.apply(imageId);
        user.changeAvatar(address, clock.instant());
        publishUpdate(users.save(user));
        return address;
    }

    @Transactional(readOnly = true)
    public Optional<StoredImage> avatarImage(String imageId) {
        return avatars.find(imageId);
    }

    @Transactional
    public void changeHandle(UserId id, Handle handle) {
        User user = require(id);
        if (!user.handle().equals(handle) && users.existsByHandle(handle)) {
            throw new ConflictException("HANDLE_TAKEN", "Cet identifiant public est déjà pris");
        }
        user.changeHandle(handle, clock.instant());
        publishUpdate(users.save(user));
    }

    @Transactional
    public void changeAccountScope(UserId id, AudienceScope scope) {
        User user = require(id);
        user.changeAccountScope(scope, clock.instant());
        publishUpdate(users.save(user));
    }

    @Transactional
    public void recordPhysiology(UserId id, Physiology physiology) {
        User user = require(id);
        user.recordPhysiology(physiology, clock.instant());
        users.save(user);
    }

    /**
     * Suppression au sens du RGPD : anonymisation. Le compte reste, vidé de tout ce qui
     * identifie la personne, parce que ses courses et ses commentaires le référencent.
     */
    @Transactional
    public void delete(UserId id) {
        User user = require(id);
        var now = clock.instant();
        user.anonymize(newAnonymousSuffix(), now);
        // Une photo qui survit à son propriétaire est une donnée personnelle orpheline.
        avatars.deleteAllOf(id);
        users.save(user);
        events.publishEvent(new UserDeleted(user.id(), now));
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
