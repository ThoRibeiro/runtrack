package com.runtrack.course.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.runtrack.shared.Distance;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CalorieEstimatorTest {

    private static final Optional<RunnerPhysiology> SEVENTY_KILOS =
            Optional.of(new RunnerPhysiology(70));

    /** Dix kilomètres en une heure, 70 kg : ~10 MET × 70 kg × 1 h ≈ 700 kcal. */
    @Test
    void estimatesFromSpeedMassAndDuration() {
        var estimate = CalorieEstimator.estimate(
                ActivityType.RUN, Distance.ofKilometers(10), Duration.ofHours(1), SEVENTY_KILOS);

        assertThat(estimate).hasValue(700);
    }

    @Test
    void scalesWithTheActivityType() {
        var running = CalorieEstimator.estimate(
                ActivityType.RUN, Distance.ofKilometers(10), Duration.ofHours(1), SEVENTY_KILOS);
        var cycling = CalorieEstimator.estimate(
                ActivityType.BIKE, Distance.ofKilometers(10), Duration.ofHours(1), SEVENTY_KILOS);

        assertThat(cycling.orElseThrow()).isLessThan(running.orElseThrow());
    }

    /**
     * Sans la masse du coureur, aucun chiffre : une valeur par défaut serait indiscernable
     * d'une estimation réelle et fausserait tous les cumuls.
     */
    @Test
    void staysAbsentWithoutThePhysiology() {
        assertThat(CalorieEstimator.estimate(
                ActivityType.RUN, Distance.ofKilometers(10), Duration.ofHours(1), Optional.empty()))
                .isEmpty();
    }

    @Test
    void staysAbsentWithoutMovementOrTime() {
        assertThat(CalorieEstimator.estimate(
                ActivityType.RUN, Distance.ZERO, Duration.ofHours(1), SEVENTY_KILOS)).isEmpty();
        assertThat(CalorieEstimator.estimate(
                ActivityType.RUN, Distance.ofKilometers(10), Duration.ZERO, SEVENTY_KILOS)).isEmpty();
        assertThat(CalorieEstimator.estimate(
                ActivityType.RUN, Distance.ofKilometers(10), Duration.ofSeconds(-1), SEVENTY_KILOS)).isEmpty();
    }

    @Test
    void refusesAnImpossibleMass() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RunnerPhysiology(0));
        assertThatIllegalArgumentException().isThrownBy(() -> new RunnerPhysiology(-70));
        assertThatIllegalArgumentException().isThrownBy(() -> new RunnerPhysiology(Double.NaN));
    }
}
