package com.runtrack.course.infrastructure.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** La table {@code activity_stats}, une ligne par course. */
@Entity
@Table(name = "activity_stats")
public class ActivityStatsEntity {

    @Id
    @Column(name = "activity_id")
    private UUID activityId;

    /** L'état complet de l'accumulateur : le seul champ qui permet de reprendre l'incrémental. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "accumulator_state", nullable = false)
    private String accumulatorState;

    @Column(name = "last_applied_sequence", nullable = false)
    private int lastAppliedSequence;

    @Column(name = "distance_meters", nullable = false)
    private double distanceMeters;

    @Column(name = "moving_time_seconds", nullable = false)
    private long movingTimeSeconds;

    @Column(name = "elevation_gain", nullable = false)
    private double elevationGain;

    @Column(name = "elevation_loss", nullable = false)
    private double elevationLoss;

    /**
     * Le verrou optimiste de l'ingestion.
     *
     * <p>C'est ici que la concurrence se produit réellement : deux lots de points envoyés
     * coup sur coup, ou un tampon rejoué pendant un retry réseau. « Un seul écrivain par
     * course » est une hypothèse de client, pas une garantie de serveur.
     */
    @Version
    private long version;

    protected ActivityStatsEntity() {
    }

    public ActivityStatsEntity(UUID activityId, String accumulatorState, int lastAppliedSequence,
            double distanceMeters, long movingTimeSeconds, double elevationGain, double elevationLoss) {
        this.activityId = activityId;
        this.accumulatorState = accumulatorState;
        this.lastAppliedSequence = lastAppliedSequence;
        this.distanceMeters = distanceMeters;
        this.movingTimeSeconds = movingTimeSeconds;
        this.elevationGain = elevationGain;
        this.elevationLoss = elevationLoss;
    }

    public UUID getActivityId() {
        return activityId;
    }

    public String getAccumulatorState() {
        return accumulatorState;
    }

    public void refreshFrom(ActivityStatsEntity other) {
        this.accumulatorState = other.accumulatorState;
        this.lastAppliedSequence = other.lastAppliedSequence;
        this.distanceMeters = other.distanceMeters;
        this.movingTimeSeconds = other.movingTimeSeconds;
        this.elevationGain = other.elevationGain;
        this.elevationLoss = other.elevationLoss;
    }
}
