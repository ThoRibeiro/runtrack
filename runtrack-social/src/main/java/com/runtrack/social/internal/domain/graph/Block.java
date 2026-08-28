package com.runtrack.social.internal.domain.graph;

import com.runtrack.shared.error.ConflictException;
import com.runtrack.shared.id.UserId;
import java.time.Instant;
import java.util.UUID;

/**
 * Un blocage, orienté : {@code blocker} bloque {@code blocked}.
 *
 * <p>Il est orienté mais ses effets ne le sont pas — bloquer rompt les abonnements dans
 * les deux sens, et l'autorisation de lecture refuse dans les deux sens. Sans quoi bloquer
 * quelqu'un le laisserait continuer à suivre ses courses.
 */
public record Block(UUID id, UserId blockerId, UserId blockedId, Instant at) {

    public Block {
        if (id == null || blockerId == null || blockedId == null || at == null) {
            throw new IllegalArgumentException("Blocage incomplet");
        }
        if (blockerId.equals(blockedId)) {
            throw new ConflictException("SELF_BLOCK", "On ne se bloque pas soi-même");
        }
    }
}
