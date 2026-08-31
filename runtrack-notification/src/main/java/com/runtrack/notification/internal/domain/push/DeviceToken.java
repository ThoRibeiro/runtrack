package com.runtrack.notification.internal.domain.push;

import com.runtrack.shared.id.UserId;
import java.time.Instant;

/**
 * Un appareil sur lequel un compte accepte de recevoir des push.
 *
 * <p>Le jeton est la seule identité qui compte, et il change tout seul : réinstallation,
 * restauration de sauvegarde, effacement des données. C'est pourquoi il est <b>lui-même</b> la
 * clé — le client réenregistre sans se demander s'il a déjà un identifiant, et un jeton
 * réattribué à un autre compte change simplement de propriétaire.
 */
public record DeviceToken(String token, UserId ownerId, DevicePlatform platform, Instant registeredAt) {

    /** Les jetons FCM tournent autour de 160 caractères ; au-delà de cette borne, ce n'en est pas un. */
    public static final int MAX_TOKEN_LENGTH = 512;

    public DeviceToken {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Un appareil sans jeton n'est pas joignable");
        }
        if (token.length() > MAX_TOKEN_LENGTH) {
            throw new IllegalArgumentException("Jeton d'appareil trop long : " + token.length());
        }
        if (ownerId == null || platform == null || registeredAt == null) {
            throw new IllegalArgumentException("Appareil incomplet");
        }
    }
}
