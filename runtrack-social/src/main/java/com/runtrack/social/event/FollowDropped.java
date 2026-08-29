package com.runtrack.social.event;

import com.runtrack.shared.id.UserId;
import java.time.Instant;

/**
 * Un abonnement a disparu : désabonnement volontaire ou demande refusée.
 *
 * <p>Existe pour que le cache des abonnés et le fil sachent qu'ils sont périmés. Sans lui,
 * le fan-out continuerait à notifier quelqu'un qui vient de se désabonner, pendant toute
 * la durée de vie de l'entrée en cache.
 */
public record FollowDropped(UserId followerId, UserId followeeId, Instant at) {
}
