package com.runtrack.course.internal.infra.jpa;

import com.runtrack.course.internal.domain.stats.ElevationSmoother;
import com.runtrack.course.internal.domain.stats.StatsAccumulator;
import com.runtrack.course.internal.domain.track.TrackPoint;
import com.runtrack.shared.measure.Distance;
import com.runtrack.shared.measure.Elevation;
import com.runtrack.shared.measure.GeoPoint;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * La forme sérialisée de l'accumulateur.
 *
 * <p>Un record de transport plat plutôt que l'accumulateur lui-même : les {@code Optional}
 * et {@code OptionalInt} du domaine n'ont pas de représentation JSON stable, et surtout un
 * modèle de persistance qui suit servilement le domaine interdit de faire évoluer l'un sans
 * casser l'autre.
 */
record StoredAccumulator(
        int lastAppliedSequence,
        StoredPoint firstPoint,
        StoredPoint lastPoint,
        double distanceMeters,
        long movingTimeNanos,
        double smootherReference,
        double elevationGain,
        double elevationLoss,
        Double minAltitude,
        Double maxAltitude,
        long heartRateSum,
        int heartRateSamples,
        Integer maxHeartRate,
        List<StoredPoint> recentWindow) {

    record StoredPoint(
            int sequenceNumber,
            double latitude,
            double longitude,
            double elevation,
            String recordedAt,
            double accuracyMeters,
            Integer heartRate,
            Integer cadence) {

        static StoredPoint from(TrackPoint point) {
            return new StoredPoint(
                    point.sequenceNumber(),
                    point.position().latitude(),
                    point.position().longitude(),
                    point.elevation().meters(),
                    point.recordedAt().toString(),
                    point.accuracyMeters(),
                    point.heartRate().isPresent() ? point.heartRate().getAsInt() : null,
                    point.cadence().isPresent() ? point.cadence().getAsInt() : null);
        }

        TrackPoint toDomain() {
            return new TrackPoint(
                    sequenceNumber,
                    new GeoPoint(latitude, longitude),
                    Elevation.ofMeters(elevation),
                    Instant.parse(recordedAt),
                    accuracyMeters,
                    heartRate == null ? OptionalInt.empty() : OptionalInt.of(heartRate),
                    cadence == null ? OptionalInt.empty() : OptionalInt.of(cadence));
        }
    }

    static StoredAccumulator from(StatsAccumulator accumulator) {
        return new StoredAccumulator(
                accumulator.lastAppliedSequence(),
                accumulator.firstPoint().map(StoredPoint::from).orElse(null),
                accumulator.lastPoint().map(StoredPoint::from).orElse(null),
                accumulator.distance().meters(),
                accumulator.movingTime().toNanos(),
                accumulator.elevation().reference().meters(),
                accumulator.elevation().gain(),
                accumulator.elevation().loss(),
                accumulator.minAltitude().map(Elevation::meters).orElse(null),
                accumulator.maxAltitude().map(Elevation::meters).orElse(null),
                accumulator.heartRateSum(),
                accumulator.heartRateSamples(),
                accumulator.maxHeartRate().isPresent() ? accumulator.maxHeartRate().getAsInt() : null,
                accumulator.recentWindow().stream().map(StoredPoint::from).toList());
    }

    StatsAccumulator toDomain() {
        return new StatsAccumulator(
                lastAppliedSequence,
                Optional.ofNullable(firstPoint).map(StoredPoint::toDomain),
                Optional.ofNullable(lastPoint).map(StoredPoint::toDomain),
                Distance.ofMeters(distanceMeters),
                Duration.ofNanos(movingTimeNanos),
                new ElevationSmoother(Elevation.ofMeters(smootherReference), elevationGain, elevationLoss),
                Optional.ofNullable(minAltitude).map(Elevation::ofMeters),
                Optional.ofNullable(maxAltitude).map(Elevation::ofMeters),
                heartRateSum,
                heartRateSamples,
                maxHeartRate == null ? OptionalInt.empty() : OptionalInt.of(maxHeartRate),
                recentWindow == null ? List.of() : recentWindow.stream().map(StoredPoint::toDomain).toList());
    }
}
