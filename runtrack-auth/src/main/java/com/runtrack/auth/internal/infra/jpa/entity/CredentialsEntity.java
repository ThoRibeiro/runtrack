package com.runtrack.auth.internal.infra.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** La table {@code credentials}. Une ligne par compte, portant la seule empreinte. */
@Entity
@Table(name = "credentials")
public class CredentialsEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "password_changed_at", nullable = false)
    private Instant passwordChangedAt;

    protected CredentialsEntity() {
    }

    public CredentialsEntity(UUID userId, String passwordHash, Instant passwordChangedAt) {
        this.userId = userId;
        this.passwordHash = passwordHash;
        this.passwordChangedAt = passwordChangedAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public void refreshFrom(CredentialsEntity other) {
        this.passwordHash = other.passwordHash;
        this.passwordChangedAt = other.passwordChangedAt;
    }
}
