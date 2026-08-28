package com.runtrack.course.internal.domain;

import com.runtrack.shared.Distance;
import com.runtrack.shared.Pace;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * La lecture d'un {@link StatsAccumulator} : il porte des cumuls, on en tire les grandeurs
 * dérivées. Aucun état, aucune accumulation ici — c'est une projection.
 */
public final class StatsCalculator {

    private StatsCalculator() {
    }

    public static ActivityStats summarize(
            StatsAccumulator accumulator,
            Duration elapsed,
            ActivityType type,
            Optional<RunnerPhysiology> physiology) {

        return new ActivityStats(
                accumulator.distance(),
                elapsed,
                accumulator.movingTime(),
                Pace.of(accumulator.distance(), accumulator.movingTime()),
                currentPace(accumulator),
                accumulator.elevation().gain(),
                accumulator.elevation().loss(),
                accumulator.minAltitude(),
                accumulator.maxAltitude(),
                averageHeartRate(accumulator),
                accumulator.maxHeartRate(),
                CalorieEstimator.estimate(type, accumulator.distance(), accumulator.movingTime(), physiology));
    }

    /**
     * L'allure sur les trente dernières secondes. L'allure moyenne d'une sortie d'une heure
     * ne bouge plus après vingt minutes : sans fenêtre glissante, le coureur n'a aucun
     * retour sur son effort du moment.
     */
    private static Optional<Pace> currentPace(StatsAccumulator accumulator) {
        List<TrackPoint> window = accumulator.recentWindow();
        if (window.size() < 2) {
            return Optional.empty();
        }
        Distance covered = Distance.ZERO;
        for (int i = 1; i < window.size(); i++) {
            covered = covered.plus(
                    DistanceCalculator.between(window.get(i - 1).position(), window.get(i).position()));
        }
        Duration span = Duration.between(
                window.getFirst().recordedAt(), window.getLast().recordedAt());
        return Pace.of(covered, span);
    }

    private static OptionalDouble averageHeartRate(StatsAccumulator accumulator) {
        return accumulator.heartRateSamples() == 0
                ? OptionalDouble.empty()
                : OptionalDouble.of((double) accumulator.heartRateSum() / accumulator.heartRateSamples());
    }
}
