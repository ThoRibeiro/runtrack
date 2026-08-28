package com.runtrack.course.internal.domain;

import com.runtrack.shared.Distance;
import com.runtrack.shared.Elevation;
import com.runtrack.shared.Pace;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Les statistiques d'une course à un instant donné.
 *
 * <p>Tout ce qui peut manquer est un {@code Optional} : sans cardiofréquencemètre il n'y
 * a pas de FC moyenne, et à l'arrêt il n'y a pas d'allure. Une valeur par défaut serait
 * indiscernable d'une mesure.
 */
public record ActivityStats(
        Distance distance,
        Duration elapsed,
        Duration movingTime,
        Optional<Pace> averagePace,
        Optional<Pace> currentPace,
        double elevationGain,
        double elevationLoss,
        Optional<Elevation> minAltitude,
        Optional<Elevation> maxAltitude,
        OptionalDouble averageHeartRate,
        OptionalInt maxHeartRate,
        OptionalInt estimatedCalories) {

    public ActivityStats {
        if (distance == null || elapsed == null || movingTime == null) {
            throw new IllegalArgumentException("Statistiques incomplètes");
        }
    }
}
