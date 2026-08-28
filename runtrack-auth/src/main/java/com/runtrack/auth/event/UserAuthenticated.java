package com.runtrack.auth.event;

import com.runtrack.shared.id.UserId;
import java.time.Instant;

/**
 * Une connexion a réussi. {@code UserRegistered} n'est pas ici : c'est {@code user} qui le
 * publie, sinon les deux modules formeraient un cycle.
 */
public record UserAuthenticated(UserId userId, Instant at) {
}
