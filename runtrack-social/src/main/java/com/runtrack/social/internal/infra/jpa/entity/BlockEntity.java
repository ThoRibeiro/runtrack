package com.runtrack.social.internal.infra.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** La table {@code blocks}. */
@Entity
@Table(name = "blocks")
public class BlockEntity {

    @Id
    private UUID id;

    @Column(name = "blocker_id", nullable = false)
    private UUID blockerId;

    @Column(name = "blocked_id", nullable = false)
    private UUID blockedId;

    @Column(name = "at", nullable = false)
    private Instant at;

    protected BlockEntity() {
    }

    public BlockEntity(UUID id, UUID blockerId, UUID blockedId, Instant at) {
        this.id = id;
        this.blockerId = blockerId;
        this.blockedId = blockedId;
        this.at = at;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBlockerId() {
        return blockerId;
    }

    public UUID getBlockedId() {
        return blockedId;
    }

    public Instant getAt() {
        return at;
    }
}
