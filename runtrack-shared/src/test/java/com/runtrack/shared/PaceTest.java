package com.runtrack.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class PaceTest {

    @Test
    void computesTimePerKilometer() {
        var pace = Pace.of(Distance.ofKilometers(2), Duration.ofMinutes(10));

        assertThat(pace).contains(new Pace(Duration.ofMinutes(5)));
    }

    @Test
    void hasNoMeaningWithoutDistance() {
        assertThat(Pace.of(Distance.ZERO, Duration.ofMinutes(5))).isEmpty();
    }

    @Test
    void hasNoMeaningWithoutTime() {
        assertThat(Pace.of(Distance.ofKilometers(1), Duration.ZERO)).isEmpty();
        assertThat(Pace.of(Distance.ofKilometers(1), Duration.ofSeconds(-1))).isEmpty();
    }

    @Test
    void convertsToSpeed() {
        Pace fiveMinutesPerKilometer = new Pace(Duration.ofMinutes(5));

        assertThat(fiveMinutesPerKilometer.metersPerSecond()).isCloseTo(3.333, org.assertj.core.data.Offset.offset(0.001));
    }

    /** Plus rapide veut dire un temps au kilomètre plus court : l'ordre suit la durée. */
    @Test
    void ordersFastestFirst() {
        Pace fast = new Pace(Duration.ofMinutes(4));
        Pace slow = new Pace(Duration.ofMinutes(6));

        assertThat(fast).isLessThan(slow);
    }

    @Test
    void refusesAnAbsentOrImpossibleDuration() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Pace(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new Pace(Duration.ZERO));
        assertThatIllegalArgumentException().isThrownBy(() -> new Pace(Duration.ofMinutes(-1)));
    }
}
