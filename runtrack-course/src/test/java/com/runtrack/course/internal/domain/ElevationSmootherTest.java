package com.runtrack.course.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.runtrack.shared.Elevation;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

class ElevationSmootherTest {

    private static final Offset<Double> CENTIMETER = Offset.offset(0.01);

    @Test
    void startsFlat() {
        ElevationSmoother smoother = ElevationSmoother.startingAt(Elevation.ofMeters(100));

        assertThat(smoother.gain()).isZero();
        assertThat(smoother.loss()).isZero();
    }

    /**
     * Le cas qui justifie tout ce composant : à l'arrêt, l'altitude d'un GPS oscille de
     * deux mètres. Additionner chaque variation positive ferait grimper le D+ sans fin.
     */
    @Test
    void ignoresSensorNoise() {
        ElevationSmoother smoother = ElevationSmoother.startingAt(Elevation.ofMeters(100));

        for (int i = 0; i < 50; i++) {
            smoother = smoother.accept(Elevation.ofMeters(i % 2 == 0 ? 102 : 98));
        }

        assertThat(smoother.gain()).isZero();
        assertThat(smoother.loss()).isZero();
    }

    @Test
    void countsARealClimb() {
        ElevationSmoother smoother = ElevationSmoother.startingAt(Elevation.ofMeters(100))
                .accept(Elevation.ofMeters(110))
                .accept(Elevation.ofMeters(120));

        assertThat(smoother.gain()).isCloseTo(20, CENTIMETER);
        assertThat(smoother.loss()).isZero();
    }

    @Test
    void countsARealDescent() {
        ElevationSmoother smoother = ElevationSmoother.startingAt(Elevation.ofMeters(120))
                .accept(Elevation.ofMeters(100));

        assertThat(smoother.gain()).isZero();
        assertThat(smoother.loss()).isCloseTo(20, CENTIMETER);
    }

    @Test
    void accumulatesClimbsAndDescentsSeparately() {
        ElevationSmoother smoother = ElevationSmoother.startingAt(Elevation.ofMeters(100))
                .accept(Elevation.ofMeters(150))
                .accept(Elevation.ofMeters(120))
                .accept(Elevation.ofMeters(170));

        assertThat(smoother.gain()).isCloseTo(100, CENTIMETER);
        assertThat(smoother.loss()).isCloseTo(30, CENTIMETER);
    }

    @Test
    void movesItsReferenceOnlyWhenItCounts() {
        ElevationSmoother ignored = ElevationSmoother.startingAt(Elevation.ofMeters(100))
                .accept(Elevation.ofMeters(102));

        assertThat(ignored.reference()).isEqualTo(Elevation.ofMeters(100));

        ElevationSmoother moved = ignored.accept(Elevation.ofMeters(104));
        assertThat(moved.reference()).isEqualTo(Elevation.ofMeters(104));
        assertThat(moved.gain()).isCloseTo(4, CENTIMETER);
    }

    @Test
    void ignoresAChangeExactlyAtTheThreshold() {
        ElevationSmoother smoother = ElevationSmoother.startingAt(Elevation.ofMeters(100))
                .accept(Elevation.ofMeters(100 + ElevationSmoother.THRESHOLD_METERS));

        assertThat(smoother.gain()).isCloseTo(ElevationSmoother.THRESHOLD_METERS, CENTIMETER);
    }

    @Test
    void refusesAnInconsistentState() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ElevationSmoother(null, 0, 0));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ElevationSmoother(Elevation.SEA_LEVEL, -1, 0));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ElevationSmoother(Elevation.SEA_LEVEL, 0, -1));
    }
}
