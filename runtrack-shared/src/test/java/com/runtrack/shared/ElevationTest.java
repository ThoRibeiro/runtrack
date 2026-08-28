package com.runtrack.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class ElevationTest {

    /** Une altitude négative est légitime : la mer Morte, ou une simple imprécision GPS. */
    @Test
    void acceptsAltitudesBelowSeaLevel() {
        assertThat(Elevation.ofMeters(-430).meters()).isEqualTo(-430);
    }

    @Test
    void measuresTheDifferenceWithAnother() {
        assertThat(Elevation.ofMeters(320).differenceWith(Elevation.ofMeters(300))).isEqualTo(20);
        assertThat(Elevation.ofMeters(300).differenceWith(Elevation.ofMeters(320))).isEqualTo(-20);
    }

    @Test
    void ordersByAltitude() {
        assertThat(Elevation.ofMeters(100)).isGreaterThan(Elevation.SEA_LEVEL);
        assertThat(Elevation.ofMeters(-5)).isLessThan(Elevation.SEA_LEVEL);
        assertThat(Elevation.SEA_LEVEL).isEqualByComparingTo(Elevation.ofMeters(0));
    }

    @Test
    void refusesNonNumericAltitudes() {
        assertThatIllegalArgumentException().isThrownBy(() -> Elevation.ofMeters(Double.NaN));
        assertThatIllegalArgumentException().isThrownBy(() -> Elevation.ofMeters(Double.NEGATIVE_INFINITY));
    }
}
