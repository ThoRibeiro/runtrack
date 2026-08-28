package com.runtrack.user.event;

import com.runtrack.shared.id.UserId;
import java.time.Instant;

/**
 * Publié par {@code user} — et non par {@code auth} — au moment où le profil est créé.
 *
 * <p>C'est {@code auth} qui appelle {@code UserApi} pour créer le compte ; si l'événement
 * partait d'{@code auth} et que {@code user} l'écoutait, les deux modules formeraient un
 * cycle.
 */
public record UserRegistered(UserId userId, String handle, Instant registeredAt) {
}
