package com.runtrack.course.internal.domain.stats;

import com.runtrack.shared.measure.Distance;
import com.runtrack.shared.measure.Pace;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Un tronçon d'un kilomètre. Le dernier d'une course est presque toujours incomplet :
 * {@code distance} le dit, et l'allure reste calculée sur la portion réellement parcourue.
 */
public record Split(
        int kilometerIndex,
        Distance distance,
        Duration time,
        Optional<Pace> pace,
        double elevationGain,
        OptionalDouble averageHeartRate) {

    public Split {
        if (kilometerIndex < 1) {
            throw new IllegalArgumentException("Un split est numéroté à partir de 1 : " + kilometerIndex);
        }
    }

    public boolean isComplete() {
        return distance.meters() >= Distance.METERS_PER_KILOMETER;
    }
}
