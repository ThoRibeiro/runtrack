package com.runtrack.course.internal.domain.fixture;

import com.runtrack.course.internal.domain.track.TrackPoint;
import com.runtrack.shared.measure.Elevation;
import com.runtrack.shared.measure.GeoPoint;
import java.time.Instant;
import java.util.OptionalInt;

/**
 * Fixture de points de trace. Aucun test n'a besoin des sept champs à la fois : le
 * builder porte des valeurs plausibles et chaque test ne nomme que ce qui le concerne,
 * ce qui rend visible l'unique variable qu'il fait bouger.
 */
public final class TrackPointBuilder {

    public static final Instant START = Instant.parse("2026-08-29T08:00:00Z");
    public static final GeoPoint LILLE = new GeoPoint(50.6292, 3.0573);

    private int sequenceNumber = 1;
    private GeoPoint position = LILLE;
    private Elevation elevation = Elevation.ofMeters(20);
    private Instant recordedAt = START;
    private double accuracyMeters = 5;
    private OptionalInt heartRate = OptionalInt.empty();
    private OptionalInt cadence = OptionalInt.empty();

    public static TrackPointBuilder aPoint() {
        return new TrackPointBuilder();
    }

    public TrackPointBuilder sequence(int value) {
        this.sequenceNumber = value;
        return this;
    }

    public TrackPointBuilder at(GeoPoint value) {
        this.position = value;
        return this;
    }

    /** Décale la position vers l'est, ce qui donne un déplacement facile à raisonner. */
    public TrackPointBuilder metersEast(double meters) {
        double degreesPerMeter = 1 / (111_320d * Math.cos(Math.toRadians(LILLE.latitude())));
        this.position = new GeoPoint(LILLE.latitude(), LILLE.longitude() + meters * degreesPerMeter);
        return this;
    }

    public TrackPointBuilder elevation(double meters) {
        this.elevation = Elevation.ofMeters(meters);
        return this;
    }

    public TrackPointBuilder secondsAfterStart(long seconds) {
        this.recordedAt = START.plusSeconds(seconds);
        return this;
    }

    public TrackPointBuilder recordedAt(Instant value) {
        this.recordedAt = value;
        return this;
    }

    public TrackPointBuilder accuracy(double meters) {
        this.accuracyMeters = meters;
        return this;
    }

    public TrackPointBuilder heartRate(int beatsPerMinute) {
        this.heartRate = OptionalInt.of(beatsPerMinute);
        return this;
    }

    public TrackPointBuilder cadence(int stepsPerMinute) {
        this.cadence = OptionalInt.of(stepsPerMinute);
        return this;
    }

    public TrackPoint build() {
        return new TrackPoint(sequenceNumber, position, elevation, recordedAt, accuracyMeters, heartRate, cadence);
    }
}
