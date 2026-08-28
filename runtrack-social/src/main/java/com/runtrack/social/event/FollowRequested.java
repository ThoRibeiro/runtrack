package com.runtrack.social.event;

import com.runtrack.shared.id.UserId;
import java.time.Instant;

/** Un compte fermé a reçu une demande d'abonnement, qu'il doit accepter ou refuser. */
public record FollowRequested(UserId followerId, UserId followeeId, Instant at) {
}
