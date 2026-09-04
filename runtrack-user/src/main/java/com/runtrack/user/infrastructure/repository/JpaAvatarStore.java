package com.runtrack.user.infrastructure.repository;

import com.runtrack.shared.id.UserId;
import com.runtrack.user.infrastructure.repository.entity.AvatarImageEntity;
import com.runtrack.user.usecases.model.profile.StoredImage;
import com.runtrack.user.usecases.port.AvatarStore;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Les photos de profil dans Postgres. Traduit, et rien d'autre.
 *
 * <p>{@code replace} supprime avant d'écrire : une image par compte, sinon chaque essai de
 * photo laisserait la précédente en base, sans plus rien pour la référencer.
 */
@Repository
class JpaAvatarStore implements AvatarStore {

    private final SpringDataAvatarRepository images;
    private final Clock clock;

    JpaAvatarStore(SpringDataAvatarRepository images, Clock clock) {
        this.images = images;
        this.clock = clock;
    }

    @Override
    public String replace(UserId owner, String contentType, byte[] bytes) {
        images.deleteAllByUserId(owner.value());
        UUID id = UUID.randomUUID();
        images.save(new AvatarImageEntity(id, owner.value(), contentType, bytes, clock.instant()));
        return id.toString();
    }

    @Override
    public Optional<StoredImage> find(String id) {
        return parse(id)
                .flatMap(images::findById)
                .map(entity -> new StoredImage(entity.id().toString(), entity.contentType(), entity.bytes()));
    }

    @Override
    public void deleteAllOf(UserId owner) {
        images.deleteAllByUserId(owner.value());
    }

    /** Un identifiant qui n'est pas un UUID est une image inexistante, pas une erreur 500. */
    private static Optional<UUID> parse(String id) {
        try {
            return Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }
}
