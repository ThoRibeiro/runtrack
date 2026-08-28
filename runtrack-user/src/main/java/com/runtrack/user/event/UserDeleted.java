package com.runtrack.user.event;

import com.runtrack.shared.id.UserId;
import java.time.Instant;

/**
 * Le compte a été anonymisé. L'identifiant reste valide : les courses, likes et
 * commentaires le référencent toujours.
 */
public record UserDeleted(UserId userId, Instant deletedAt) {
}
