package com.runtrack.user.usecases.fixture;

import com.runtrack.shared.id.UserId;
import com.runtrack.user.usecases.model.profile.StoredImage;
import com.runtrack.user.usecases.port.AvatarStore;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Le stockage des photos, en mémoire : les cas d'usage n'ont pas besoin de Postgres. */
public final class InMemoryAvatarStore implements AvatarStore {

    private final Map<String, StoredImage> images = new LinkedHashMap<>();
    private final Map<String, UserId> owners = new LinkedHashMap<>();

    @Override
    public String replace(UserId owner, String contentType, byte[] bytes) {
        deleteAllOf(owner);
        String id = UUID.randomUUID().toString();
        images.put(id, new StoredImage(id, contentType, bytes));
        owners.put(id, owner);
        return id;
    }

    @Override
    public Optional<StoredImage> find(String id) {
        return Optional.ofNullable(images.get(id));
    }

    @Override
    public void deleteAllOf(UserId owner) {
        owners.entrySet().removeIf(entry -> {
            if (!entry.getValue().equals(owner)) {
                return false;
            }
            images.remove(entry.getKey());
            return true;
        });
    }

    /** Ce que le test veut savoir : combien d'images survivent. */
    public int size() {
        return images.size();
    }
}
