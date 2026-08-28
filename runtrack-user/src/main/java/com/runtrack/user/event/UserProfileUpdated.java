package com.runtrack.user.event;

import com.runtrack.shared.id.UserId;
import java.time.Instant;

/** Invalide les caches de profil et de visibilité (§6). */
public record UserProfileUpdated(UserId userId, String handle, Instant updatedAt) {
}
