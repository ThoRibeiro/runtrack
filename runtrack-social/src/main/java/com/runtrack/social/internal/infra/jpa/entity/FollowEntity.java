package com.runtrack.social.internal.infra.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** La table {@code follows}. */
@Entity
@Table(name = "follows")
public class FollowEntity {

    @Id
    private UUID id;

    @Column(name = "follower_id", nullable = false)
    private UUID followerId;

    @Column(name = "followee_id", nullable = false)
    private UUID followeeId;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    protected FollowEntity() {
    }

    public FollowEntity(UUID id, UUID followerId, UUID followeeId, String status,
            Instant requestedAt, Instant acceptedAt) {
        this.id = id;
        this.followerId = followerId;
        this.followeeId = followeeId;
        this.status = status;
        this.requestedAt = requestedAt;
        this.acceptedAt = acceptedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getFollowerId() {
        return followerId;
    }

    public UUID getFolloweeId() {
        return followeeId;
    }

    public String getStatus() {
        return status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void refreshFrom(FollowEntity other) {
        this.status = other.status;
        this.acceptedAt = other.acceptedAt;
    }
}
