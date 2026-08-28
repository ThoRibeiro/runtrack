package com.runtrack.social.event;

import com.runtrack.shared.id.UserId;
import java.time.Instant;

/**
 * Un abonnement est actif. Émis aussi bien à l'acceptation d'une demande qu'à l'abonnement
 * immédiat sur un compte public : les caches d'abonnés et le fil s'invalident pareil.
 */
public record FollowAccepted(UserId followerId, UserId followeeId, Instant at) {
}
