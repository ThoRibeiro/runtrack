package com.runtrack.notification.internal.application.port;

import com.runtrack.notification.internal.domain.push.DeviceToken;
import com.runtrack.notification.internal.domain.push.PushMessage;
import java.util.List;
import java.util.Set;

/**
 * L'envoi vers le service de push, quel qu'il soit.
 *
 * <p>Port sortant : le cas d'usage sait qu'il faut prévenir des appareils, il ne sait pas si cela
 * passe par Firebase, par Apple ou par une ligne de journal. Le choix se fait par configuration
 * (§7), jamais par un {@code if} dans la logique d'envoi.
 *
 * <p>La méthode prend une <b>liste</b> d'appareils et non un appareil : envoyer un par un serait
 * le contraire de ce que demande le §7, et un fan-out de mille abonnés le rendrait visible tout de
 * suite.
 */
public interface PushSender {

    Result send(List<DeviceToken> devices, PushMessage message);

    /**
     * @param delivered nombre d'appareils que le service a acceptés
     * @param invalidTokens jetons que le service a déclarés inconnus ou révoqués — à effacer
     */
    record Result(int delivered, Set<String> invalidTokens) {

        public static final Result NOTHING = new Result(0, Set.of());

        public Result {
            invalidTokens = Set.copyOf(invalidTokens);
        }
    }
}
