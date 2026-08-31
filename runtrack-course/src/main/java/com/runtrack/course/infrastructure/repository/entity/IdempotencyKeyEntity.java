package com.runtrack.course.infrastructure.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** La table {@code idempotency_keys}, dont la clé est le couple (course, clé client). */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKeyEntity {

    @EmbeddedId
    private Id id;

    @Column(name = "request_digest", nullable = false, length = 64)
    private String requestDigest;

    @Column(name = "response_body", nullable = false, columnDefinition = "text")
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyKeyEntity() {
    }

    public IdempotencyKeyEntity(UUID activityId, String key, String requestDigest,
            String responseBody, Instant createdAt) {
        this.id = new Id(activityId, key);
        this.requestDigest = requestDigest;
        this.responseBody = responseBody;
        this.createdAt = createdAt;
    }

    public String getRequestDigest() {
        return requestDigest;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public static Id idOf(UUID activityId, String key) {
        return new Id(activityId, key);
    }

    /** La clé composite : une clé client n'a de portée qu'au sein d'une course. */
    @Embeddable
    public static class Id implements Serializable {

        @Column(name = "activity_id", nullable = false)
        private UUID activityId;

        @Column(name = "idempotency_key", nullable = false, length = 200)
        private String idempotencyKey;

        protected Id() {
        }

        Id(UUID activityId, String idempotencyKey) {
            this.activityId = activityId;
            this.idempotencyKey = idempotencyKey;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Id that
                    && Objects.equals(activityId, that.activityId)
                    && Objects.equals(idempotencyKey, that.idempotencyKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(activityId, idempotencyKey);
        }
    }
}
