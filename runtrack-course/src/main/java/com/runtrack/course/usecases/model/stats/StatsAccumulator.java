package com.runtrack.course.usecases.model.stats;

import com.runtrack.course.usecases.model.track.DistanceCalculator;
import com.runtrack.course.usecases.model.track.TrackPoint;
import com.runtrack.shared.measure.Distance;
import com.runtrack.shared.measure.Elevation;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * L'état incrémental des statistiques d'une course.
 *
 * <p>Une sortie de trois heures ne se recalcule jamais depuis le premier point à chaque
 * lot reçu : {@link #apply(TrackPoint)} avance d'un point et rend un nouvel accumulateur.
 *
 * <p>Un accumulateur incrémental est par nature sensible à l'ordre et aux doublons. Trois
 * choses le rendent malgré tout sûr au rejeu :
 * <ol>
 *   <li>{@code lastAppliedSequence} sert de curseur — un point déjà appliqué est ignoré
 *       ici même, en plus de l'être par le filtre ;</li>
 *   <li>{@link #apply(TrackPoint)} est une fonction pure, sans effet de bord ;</li>
 *   <li>rejouer depuis {@link #empty()} la totalité des points acceptés redonne
 *       exactement le même accumulateur. C'est la propriété que teste
 *       {@code StatsAccumulatorTest}, et la seule qui prouve réellement l'idempotence.</li>
 * </ol>
 */
public record StatsAccumulator(
        int lastAppliedSequence,
        Optional<TrackPoint> firstPoint,
        Optional<TrackPoint> lastPoint,
        Distance distance,
        Duration movingTime,
        ElevationSmoother elevation,
        Optional<Elevation> minAltitude,
        Optional<Elevation> maxAltitude,
        long heartRateSum,
        int heartRateSamples,
        OptionalInt maxHeartRate,
        List<TrackPoint> recentWindow) {

    /** En deçà, le coureur est à l'arrêt : le temps s'écoule mais pas le temps en mouvement. */
    public static final double MOVING_THRESHOLD_METERS_PER_SECOND = 0.5;

    /** La fenêtre glissante servant à l'allure instantanée. */
    public static final Duration INSTANT_PACE_WINDOW = Duration.ofSeconds(30);

    private static final int NO_SEQUENCE_APPLIED = -1;

    public StatsAccumulator {
        recentWindow = List.copyOf(recentWindow);
    }

    public static StatsAccumulator empty() {
        return new StatsAccumulator(
                NO_SEQUENCE_APPLIED, Optional.empty(), Optional.empty(),
                Distance.ZERO, Duration.ZERO, ElevationSmoother.startingAt(Elevation.SEA_LEVEL),
                Optional.empty(), Optional.empty(), 0, 0, OptionalInt.empty(), List.of());
    }

    public StatsAccumulator apply(TrackPoint point) {
        if (point.sequenceNumber() <= lastAppliedSequence) {
            return this;
        }
        return lastPoint.map(previous -> advanceFrom(previous, point)).orElseGet(() -> startWith(point));
    }

    private StatsAccumulator startWith(TrackPoint point) {
        return new StatsAccumulator(
                point.sequenceNumber(), Optional.of(point), Optional.of(point),
                Distance.ZERO, Duration.ZERO, ElevationSmoother.startingAt(point.elevation()),
                Optional.of(point.elevation()), Optional.of(point.elevation()),
                heartRateSumWith(point), heartRateSamplesWith(point), maxHeartRateWith(point),
                List.of(point));
    }

    private StatsAccumulator advanceFrom(TrackPoint previous, TrackPoint point) {
        Distance segment = DistanceCalculator.between(previous.position(), point.position());
        Duration segmentTime = Duration.between(previous.recordedAt(), point.recordedAt());

        return new StatsAccumulator(
                point.sequenceNumber(),
                firstPoint,
                Optional.of(point),
                distance.plus(segment),
                movingTime.plus(movingPartOf(segment, segmentTime)),
                elevation.accept(point.elevation()),
                Optional.of(lower(minAltitude, point.elevation())),
                Optional.of(higher(maxAltitude, point.elevation())),
                heartRateSumWith(point),
                heartRateSamplesWith(point),
                maxHeartRateWith(point),
                windowEndingAt(point));
    }

    private static Duration movingPartOf(Distance segment, Duration segmentTime) {
        if (segmentTime.isZero() || segmentTime.isNegative()) {
            return Duration.ZERO;
        }
        double seconds = segmentTime.toNanos() / 1_000_000_000d;
        boolean moving = segment.meters() / seconds >= MOVING_THRESHOLD_METERS_PER_SECOND;
        return moving ? segmentTime : Duration.ZERO;
    }

    private List<TrackPoint> windowEndingAt(TrackPoint point) {
        var window = new ArrayList<TrackPoint>();
        var cutoff = point.recordedAt().minus(INSTANT_PACE_WINDOW);
        for (TrackPoint kept : recentWindow) {
            if (!kept.recordedAt().isBefore(cutoff)) {
                window.add(kept);
            }
        }
        window.add(point);
        return window;
    }

    private long heartRateSumWith(TrackPoint point) {
        return point.heartRate().isPresent() ? heartRateSum + point.heartRate().getAsInt() : heartRateSum;
    }

    private int heartRateSamplesWith(TrackPoint point) {
        return point.heartRate().isPresent() ? heartRateSamples + 1 : heartRateSamples;
    }

    private OptionalInt maxHeartRateWith(TrackPoint point) {
        if (point.heartRate().isEmpty()) {
            return maxHeartRate;
        }
        int measured = point.heartRate().getAsInt();
        return maxHeartRate.isPresent()
                ? OptionalInt.of(Math.max(maxHeartRate.getAsInt(), measured))
                : OptionalInt.of(measured);
    }

    private static Elevation lower(Optional<Elevation> current, Elevation candidate) {
        return current.filter(known -> known.compareTo(candidate) <= 0).orElse(candidate);
    }

    private static Elevation higher(Optional<Elevation> current, Elevation candidate) {
        return current.filter(known -> known.compareTo(candidate) >= 0).orElse(candidate);
    }
}
