package com.runtrack.social.internal.domain.graph;

import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.error.ConflictException;
import com.runtrack.shared.id.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Un abonnement, de {@code follower} vers {@code followee}.
 *
 * <p>La portée du compte suivi décide seule de l'état initial : un compte public accepte
 * d'emblée, un compte fermé génère une demande. Faire porter ce choix par l'appelant
 * ouvrirait la porte à un contrôleur qui l'oublie.
 */
public final class Follow {

    private final UUID id;
    private final UserId followerId;
    private final UserId followeeId;
    private final Instant requestedAt;

    private FollowStatus status;
    private Instant acceptedAt;

    private Follow(UUID id, UserId followerId, UserId followeeId, FollowStatus status,
            Instant requestedAt, Instant acceptedAt) {
        if (id == null || followerId == null || followeeId == null
                || status == null || requestedAt == null) {
            throw new IllegalArgumentException("Abonnement incomplet");
        }
        if (followerId.equals(followeeId)) {
            throw new ConflictException("SELF_FOLLOW", "On ne s'abonne pas à soi-même");
        }
        this.id = id;
        this.followerId = followerId;
        this.followeeId = followeeId;
        this.status = status;
        this.requestedAt = requestedAt;
        this.acceptedAt = acceptedAt;
    }

    public static Follow request(UUID id, UserId followerId, UserId followeeId,
            AudienceScope followeeScope, Instant at) {

        boolean automatic = followeeScope == AudienceScope.PUBLIC;
        return new Follow(id, followerId, followeeId,
                automatic ? FollowStatus.ACCEPTED : FollowStatus.PENDING,
                at, automatic ? at : null);
    }

    public static Follow rehydrate(UUID id, UserId followerId, UserId followeeId,
            FollowStatus status, Instant requestedAt, Instant acceptedAt) {
        return new Follow(id, followerId, followeeId, status, requestedAt, acceptedAt);
    }

    public void accept(Instant at) {
        if (status.isAccepted()) {
            throw new ConflictException("FOLLOW_ALREADY_ACCEPTED", "Cette demande est déjà acceptée");
        }
        this.status = FollowStatus.ACCEPTED;
        this.acceptedAt = at;
    }

    /** Seul le compte suivi décide de sa propre liste d'abonnés. */
    public void requireOwnedBy(UserId candidate) {
        if (!followeeId.equals(candidate)) {
            throw new ConflictException("NOT_YOUR_REQUEST", "Cette demande ne vous est pas adressée");
        }
    }

    public boolean isAccepted() {
        return status.isAccepted();
    }

    public UUID id() {
        return id;
    }

    public UserId followerId() {
        return followerId;
    }

    public UserId followeeId() {
        return followeeId;
    }

    public FollowStatus status() {
        return status;
    }

    public Instant requestedAt() {
        return requestedAt;
    }

    public Optional<Instant> acceptedAt() {
        return Optional.ofNullable(acceptedAt);
    }
}
