package com.runtrack.user.usecases.model.profile;

import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.error.ConflictException;
import com.runtrack.shared.id.UserId;
import java.time.Instant;
import java.util.Optional;

/**
 * L'agrégat racine du profil.
 *
 * <p>La suppression est une <em>anonymisation</em>, pas un effacement : les courses, les
 * likes et les commentaires référencent l'identifiant, et les faire disparaître réécrirait
 * l'historique d'autres utilisateurs. Ce qui part est tout ce qui identifie la personne ;
 * ce qui reste est l'identifiant technique et la date de création.
 */
public final class User {

    private static final int MAX_DISPLAY_NAME = 80;
    private static final int MAX_BIO = 500;
    private static final String DELETED_EMAIL_DOMAIN = "deleted.invalid";

    private final UserId id;
    private final Instant registeredAt;

    private Handle handle;
    private Email email;
    private String displayName;
    private String avatarUrl;
    private String bio;
    private AudienceScope accountScope;
    private AccountStatus status;
    private Physiology physiology;

    private User(UserId id, Handle handle, Email email, String displayName, Instant registeredAt) {
        this.id = id;
        this.handle = handle;
        this.email = email;
        this.registeredAt = registeredAt;
        this.accountScope = AudienceScope.PUBLIC;
        this.status = AccountStatus.PENDING_VERIFICATION;
        this.physiology = Physiology.UNKNOWN;
        this.displayName = requireDisplayName(displayName);
    }

    public static User register(UserId id, Handle handle, Email email, String displayName, Instant at) {
        if (id == null || handle == null || email == null || at == null) {
            throw new IllegalArgumentException("Inscription incomplète");
        }
        return new User(id, handle, email, displayName, at);
    }

    /**
     * Restaure un agrégat déjà persisté. Réservé à la couche de persistance : elle rend un
     * état qui a déjà satisfait les invariants, elle ne les rejoue pas.
     */
    public static User rehydrate(UserId id, Handle handle, Email email, String displayName,
            String avatarUrl, String bio, AudienceScope accountScope, AccountStatus status,
            Physiology physiology, Instant registeredAt) {
        User user = new User(id, handle, email, displayName, registeredAt);
        user.avatarUrl = avatarUrl;
        user.bio = bio;
        user.accountScope = accountScope;
        user.status = status;
        user.physiology = physiology;
        return user;
    }

    public void verifyEmail() {
        if (status != AccountStatus.PENDING_VERIFICATION) {
            throw new ConflictException("EMAIL_ALREADY_VERIFIED", "Cette adresse est déjà confirmée");
        }
        status = AccountStatus.ACTIVE;
    }

    public void updateProfile(String newDisplayName, String newBio, String newAvatarUrl) {
        requireActive();
        this.displayName = requireDisplayName(newDisplayName);
        this.bio = trimmedOrNull(newBio, MAX_BIO, "Biographie");
        this.avatarUrl = trimmedOrNull(newAvatarUrl, 2_000, "URL d'avatar");
    }

    public void changeHandle(Handle newHandle) {
        requireActive();
        if (newHandle == null) {
            throw new IllegalArgumentException("Identifiant public absent");
        }
        this.handle = newHandle;
    }

    public void changeAccountScope(AudienceScope newScope) {
        requireActive();
        if (newScope == null) {
            throw new IllegalArgumentException("Portée de visibilité absente");
        }
        this.accountScope = newScope;
    }

    public void recordPhysiology(Physiology newPhysiology) {
        requireActive();
        if (newPhysiology == null) {
            throw new IllegalArgumentException("Physiologie absente : utiliser Physiology.UNKNOWN");
        }
        this.physiology = newPhysiology;
    }

    public void suspend() {
        if (status == AccountStatus.DELETED) {
            throw new ConflictException("ACCOUNT_DELETED", "Ce compte est supprimé");
        }
        status = AccountStatus.SUSPENDED;
    }

    /**
     * Anonymise le compte, sans le détruire.
     *
     * <p>Effacé : identifiant public, adresse e-mail, nom affiché, biographie, avatar et
     * toute la physiologie. Conservé : l'identifiant technique, parce que les courses le
     * référencent, et la date d'inscription, qui n'identifie personne.
     */
    public void anonymize(String anonymousSuffix) {
        if (status == AccountStatus.DELETED) {
            throw new ConflictException("ACCOUNT_DELETED", "Ce compte est déjà supprimé");
        }
        if (anonymousSuffix == null || anonymousSuffix.isBlank()) {
            throw new IllegalArgumentException("Suffixe d'anonymisation absent");
        }
        this.handle = new Handle("deleted-" + anonymousSuffix);
        this.email = new Email("deleted-" + anonymousSuffix + "@" + DELETED_EMAIL_DOMAIN);
        this.displayName = "Compte supprimé";
        this.bio = null;
        this.avatarUrl = null;
        this.physiology = Physiology.UNKNOWN;
        this.accountScope = AudienceScope.PRIVATE;
        this.status = AccountStatus.DELETED;
    }

    private void requireActive() {
        if (!status.canAct()) {
            throw new ConflictException("ACCOUNT_NOT_ACTIVE",
                    "Ce compte ne peut pas être modifié dans son état actuel");
        }
    }

    private static String requireDisplayName(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            throw new IllegalArgumentException("Nom affiché absent");
        }
        String trimmed = candidate.strip();
        if (trimmed.length() > MAX_DISPLAY_NAME) {
            throw new IllegalArgumentException("Nom affiché trop long : " + trimmed.length() + " caractères");
        }
        return trimmed;
    }

    private static String trimmedOrNull(String candidate, int maxLength, String label) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        String trimmed = candidate.strip();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(label + " trop longue : " + trimmed.length() + " caractères");
        }
        return trimmed;
    }

    public UserId id() {
        return id;
    }

    public Handle handle() {
        return handle;
    }

    public Email email() {
        return email;
    }

    public String displayName() {
        return displayName;
    }

    public Optional<String> avatarUrl() {
        return Optional.ofNullable(avatarUrl);
    }

    public Optional<String> bio() {
        return Optional.ofNullable(bio);
    }

    public AudienceScope accountScope() {
        return accountScope;
    }

    public AccountStatus status() {
        return status;
    }

    public Physiology physiology() {
        return physiology;
    }

    public Instant registeredAt() {
        return registeredAt;
    }
}
