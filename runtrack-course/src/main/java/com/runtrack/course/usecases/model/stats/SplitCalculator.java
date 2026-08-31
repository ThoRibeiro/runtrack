package com.runtrack.course.usecases.model.stats;

import com.runtrack.course.usecases.model.track.DistanceCalculator;
import com.runtrack.course.usecases.model.track.TrackPoint;
import com.runtrack.shared.measure.Distance;
import com.runtrack.shared.measure.Pace;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Le découpage d'une trace en tronçons d'un kilomètre.
 *
 * <p>Le kilomètre tombe presque toujours entre deux points GPS. Plutôt que de couper au
 * point suivant — ce qui décale chaque split un peu plus que le précédent —, l'instant de
 * franchissement est interpolé linéairement sur le segment qui l'enjambe.
 *
 * <p>Approximation retenue : l'altitude de référence du split suivant est celle du point
 * qui suit le franchissement, pas l'altitude interpolée. L'écart porte sur quelques
 * mètres de relief, très en deçà du seuil d'hystérésis du lisseur.
 */
public final class SplitCalculator {

    private SplitCalculator() {
    }

    public static List<Split> byKilometer(List<TrackPoint> points) {
        if (points.size() < 2) {
            return List.of();
        }

        var splits = new ArrayList<Split>();
        var current = new SplitInProgress(1, points.getFirst());

        for (int i = 1; i < points.size(); i++) {
            TrackPoint previous = points.get(i - 1);
            TrackPoint point = points.get(i);

            double remaining = DistanceCalculator.between(previous.position(), point.position()).meters();
            Duration segmentTime = Duration.between(previous.recordedAt(), point.recordedAt());
            Instant segmentStart = previous.recordedAt();

            while (remaining > 0 && current.distance + remaining >= Distance.METERS_PER_KILOMETER) {
                double needed = Distance.METERS_PER_KILOMETER - current.distance;
                double fraction = needed / remaining;
                Instant crossing = segmentStart.plusNanos(Math.round(segmentTime.toNanos() * fraction));

                splits.add(current.close(Distance.ofKilometers(1), crossing));

                remaining -= needed;
                segmentTime = Duration.between(crossing, point.recordedAt());
                segmentStart = crossing;
                current = new SplitInProgress(current.index + 1, crossing, point.elevation());
            }

            current.distance += remaining;
            current.accept(point);
        }

        if (current.distance > 0) {
            splits.add(current.close(Distance.ofMeters(current.distance), points.getLast().recordedAt()));
        }
        return List.copyOf(splits);
    }

    /** L'état mutable et local d'un split en cours de construction. Ne sort jamais d'ici. */
    private static final class SplitInProgress {

        private final int index;
        private final Instant startedAt;
        private double distance;
        private ElevationSmoother elevation;
        private long heartRateSum;
        private int heartRateSamples;

        private SplitInProgress(int index, TrackPoint first) {
            this(index, first.recordedAt(), first.elevation());
        }

        private SplitInProgress(int index, Instant startedAt, com.runtrack.shared.measure.Elevation reference) {
            this.index = index;
            this.startedAt = startedAt;
            this.elevation = ElevationSmoother.startingAt(reference);
        }

        private void accept(TrackPoint point) {
            elevation = elevation.accept(point.elevation());
            if (point.heartRate().isPresent()) {
                heartRateSum += point.heartRate().getAsInt();
                heartRateSamples++;
            }
        }

        private Split close(Distance covered, Instant endedAt) {
            Duration time = Duration.between(startedAt, endedAt);
            return new Split(
                    index,
                    covered,
                    time,
                    Pace.of(covered, time),
                    elevation.gain(),
                    heartRateSamples == 0
                            ? OptionalDouble.empty()
                            : OptionalDouble.of((double) heartRateSum / heartRateSamples));
        }
    }
}
