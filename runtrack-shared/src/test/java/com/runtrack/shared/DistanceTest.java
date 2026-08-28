package com.runtrack.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class DistanceTest {

    @Test
    void convertsBetweenMetersAndKilometers() {
        assertThat(Distance.ofKilometers(5).meters()).isEqualTo(5_000);
        assertThat(Distance.ofMeters(2_500).toKilometers()).isEqualTo(2.5);
    }

    @Test
    void adds() {
        assertThat(Distance.ofMeters(300).plus(Distance.ofMeters(700)))
                .isEqualTo(Distance.ofKilometers(1));
    }

    @Test
    void knowsWhenItIsZero() {
        assertThat(Distance.ZERO.isZero()).isTrue();
        assertThat(Distance.ofMeters(0.5).isZero()).isFalse();
    }

    @Test
    void ordersByLength() {
        assertThat(Distance.ofMeters(100)).isLessThan(Distance.ofMeters(200));
        assertThat(Distance.ofMeters(200)).isGreaterThan(Distance.ofMeters(100));
        assertThat(Distance.ofMeters(100)).isEqualByComparingTo(Distance.ofMeters(100));
    }

    @Test
    void refusesNegativeLengths() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Distance.ofMeters(-1))
                .withMessageContaining("négative");
    }

    @Test
    void refusesNonNumericLengths() {
        assertThatIllegalArgumentException().isThrownBy(() -> Distance.ofMeters(Double.NaN));
        assertThatIllegalArgumentException().isThrownBy(() -> Distance.ofMeters(Double.POSITIVE_INFINITY));
    }
}
