package com.runtrack.user.usecases.port;

import com.runtrack.shared.id.UserId;
import com.runtrack.user.usecases.model.profile.StoredImage;
import java.util.Optional;

/**
 * Où vivent les photos de profil.
 *
 * <p>Le port ne dit pas « base de données » : l'implémentation d'aujourd'hui écrit dans
 * Postgres, celle d'un jour où les images pèseront lourd écrira dans un stockage objet, et
 * le cas d'usage ne changera pas d'une ligne.
 */
public interface AvatarStore {

    /** Remplace l'image du compte et rend l'identifiant de la nouvelle. */
    String replace(UserId owner, String contentType, byte[] bytes);

    Optional<StoredImage> find(String id);

    /** À la suppression du compte : une photo qui survit à son propriétaire est une fuite. */
    void deleteAllOf(UserId owner);
}
