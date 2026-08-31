package com.runtrack.notification.internal.domain.push;

/**
 * Ce qui s'affiche sur l'écran verrouillé.
 *
 * <p>{@code deepLink} accompagne le message : le §7 veut qu'un push sur « ton ami vient de
 * démarrer une course » ouvre directement le suivi live.
 */
public record PushMessage(String title, String body, String deepLink) {

    public PushMessage {
        if (title == null || title.isBlank() || body == null || body.isBlank() || deepLink == null) {
            throw new IllegalArgumentException("Message push incomplet");
        }
    }
}
