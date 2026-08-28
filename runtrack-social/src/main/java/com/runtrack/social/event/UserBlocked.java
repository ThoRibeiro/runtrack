package com.runtrack.social.event;

import com.runtrack.shared.id.UserId;
import java.time.Instant;

/** Un blocage a été posé, et les abonnements des deux côtés ont été rompus. */
public record UserBlocked(UserId blockerId, UserId blockedId, Instant at) {
}
