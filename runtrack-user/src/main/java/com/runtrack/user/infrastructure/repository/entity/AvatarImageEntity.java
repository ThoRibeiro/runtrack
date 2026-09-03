package com.runtrack.user.infrastructure.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * La table {@code avatar_images}. Les octets d'une photo de profil, rien d'autre.
 *
 * <p>Un {@code byte[]} nu, et surtout **pas** {@code @Lob} : sur Postgres, {@code @Lob}
 * demande un {@code oid} — un large object rangé hors de la table, avec son propre cycle
 * de vie — et la validation du schéma échoue au démarrage face à une colonne {@code BYTEA}.
 * Une vignette de deux mégaoctets tient très bien dans un {@code BYTEA}.
 */
@Entity
@Table(name = "avatar_images")
public class AvatarImageEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(nullable = false)
    private byte[] bytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AvatarImageEntity() {
        // JPA.
    }

    public AvatarImageEntity(UUID id, UUID userId, String contentType, byte[] bytes, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.contentType = contentType;
        this.bytes = bytes;
        this.createdAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public String contentType() {
        return contentType;
    }

    public byte[] bytes() {
        return bytes;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
