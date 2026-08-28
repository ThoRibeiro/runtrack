package com.runtrack.course.internal.domain.track;

import com.runtrack.shared.measure.Distance;
import static org.assertj.core.api.Assertions.assertThat;

import com.runtrack.shared.measure.GeoPoint;
import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.Test;

class DistanceCalculatorTest {

    private static final GeoPoint LILLE = new GeoPoint(50.6292, 3.0573);
    private static final GeoPoint PARIS = new GeoPoint(48.8566, 2.3522);

    /** Distance orthodromique Lille–Paris, environ 204 km selon les tables usuelles. */
    @Test
    void matchesAKnownLongDistance() {
        assertThat(DistanceCalculator.between(LILLE, PARIS).meters())
                .isCloseTo(204_000, Percentage.withPercentage(1));
    }

    @Test
    void isZeroBetweenAPointAndItself() {
        assertThat(DistanceCalculator.between(LILLE, LILLE).meters()).isZero();
    }

    @Test
    void isSymmetric() {
        assertThat(DistanceCalculator.between(LILLE, PARIS))
                .isEqualTo(DistanceCalculator.between(PARIS, LILLE));
    }

    /** Un degré de latitude fait très exactement un centième de la circonférence polaire. */
    @Test
    void matchesOneDegreeOfLatitude() {
        double oneDegree = DistanceCalculator.between(new GeoPoint(0, 0), new GeoPoint(1, 0)).meters();

        assertThat(oneDegree).isCloseTo(111_195, Percentage.withPercentage(0.1));
    }

    /** À l'échelle d'un pas de GPS, la résolution reste métrique. */
    @Test
    void resolvesShortSegments() {
        GeoPoint tenMetersNorth = new GeoPoint(LILLE.latitude() + 10 / 111_195d, LILLE.longitude());

        assertThat(DistanceCalculator.between(LILLE, tenMetersNorth).meters())
                .isCloseTo(10, Percentage.withPercentage(1));
    }

    @Test
    void handlesTheAntimeridian() {
        double acrossTheLine = DistanceCalculator.between(
                new GeoPoint(0, 179.99), new GeoPoint(0, -179.99)).meters();

        assertThat(acrossTheLine).isCloseTo(2_224, Percentage.withPercentage(1));
    }
}
