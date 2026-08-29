package com.runtrack.course.internal.infra.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** La table {@code activities}. */
@Entity
@Table(name = "activities")
public class ActivityEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 16)
    private String type;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 2_000)
    private String description;

    @Column(name = "activity_scope", nullable = false, length = 16)
    private String activityScope;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "status_since", nullable = false)
    private Instant statusSince;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "clock_skew_nanos", nullable = false)
    private long clockSkewNanos;

    /**
     * Le verrou optimiste de l'agrégat. Il porte sur les transitions d'état, pas sur les
     * statistiques : celles-ci vivent dans leur propre table, sinon chaque lot de points
     * entrerait en conflit avec une mise en pause.
     */
    @Version
    private long version;

    protected ActivityEntity() {
    }

    public ActivityEntity(UUID id, UUID ownerId, String type, String title, String description,
            String activityScope, String status, Instant statusSince, Instant startedAt, long clockSkewNanos) {
        this.id = id;
        this.ownerId = ownerId;
        this.type = type;
        this.title = title;
        this.description = description;
        this.activityScope = activityScope;
        this.status = status;
        this.statusSince = statusSince;
        this.startedAt = startedAt;
        this.clockSkewNanos = clockSkewNanos;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getActivityScope() {
        return activityScope;
    }

    public String getStatus() {
        return status;
    }

    public Instant getStatusSince() {
        return statusSince;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public long getClockSkewNanos() {
        return clockSkewNanos;
    }

    public void refreshFrom(ActivityEntity other) {
        this.title = other.title;
        this.description = other.description;
        this.activityScope = other.activityScope;
        this.status = other.status;
        this.statusSince = other.statusSince;
    }
}
