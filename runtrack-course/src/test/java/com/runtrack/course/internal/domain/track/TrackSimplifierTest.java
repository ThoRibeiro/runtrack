package com.runtrack.course.internal.domain.track;

import static com.runtrack.course.internal.domain.fixture.TrackPointBuilder.aPoint;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class TrackSimplifierTest {

    private static List<TrackPoint> straightLineOf(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(index -> aPoint().sequence(index).metersEast(10d * index)
                        .secondsAfterStart(index).build())
                .toList();
    }

    /** Une ligne droite ne garde que ses deux extrémités : tout le reste est du pixel identique. */
    @Test
    void aStraightLineKeepsOnlyItsEnds() {
        List<TrackPoint> simplified = TrackSimplifier.simplify(straightLineOf(100), 5);

        assertThat(simplified).hasSize(2);
        assertThat(simplified.getFirst().sequenceNumber()).isEqualTo(1);
        assertThat(simplified.getLast().sequenceNumber()).isEqualTo(100);
    }

    /** Ce qui change la forme du tracé est conservé, sinon la carte mentirait. */
    @Test
    void aDetourIsKept() {
        var points = new java.util.ArrayList<>(straightLineOf(10));
        points.add(5, aPoint().sequence(99).at(new com.runtrack.shared.measure.GeoPoint(
                50.6302, 3.0573)).secondsAfterStart(5).build());

        List<TrackPoint> simplified = TrackSimplifier.simplify(points, 5);

        assertThat(simplified).extracting(TrackPoint::sequenceNumber).contains(99);
    }

    @Test
    void aTrackOfTwoPointsOrFewerIsReturnedUntouched() {
        assertThat(TrackSimplifier.simplify(straightLineOf(2), 5)).hasSize(2);
        assertThat(TrackSimplifier.simplify(List.of(), 5)).isEmpty();
    }

    /** Plus la tolérance est large, moins il reste de points — jamais l'inverse. */
    @Test
    void aWiderToleranceNeverKeepsMorePoints() {
        var points = new java.util.ArrayList<>(straightLineOf(20));
        points.add(10, aPoint().sequence(99).at(new com.runtrack.shared.measure.GeoPoint(
                50.62925, 3.0573)).secondsAfterStart(10).build());

        int tight = TrackSimplifier.simplify(points, 1).size();
        int loose = TrackSimplifier.simplify(points, 100).size();

        assertThat(loose).isLessThanOrEqualTo(tight);
    }

    /**
     * Dix mille points presque alignés : le cas qui fait déborder la pile d'une version récursive,
     * et le plus fréquent en pratique.
     *
     * <p>Il n'en reste pas deux, et c'est normal : sur cent kilomètres, la courbure de la Terre
     * écarte le trajet réel de sa corde de bien plus de cinq mètres. Ce qui est vérifié ici, c'est
     * que dix mille points passent — et qu'il en ressort un ordre de grandeur de moins.
     */
    @Test
    void aVeryLongAlmostStraightTrackDoesNotBlowTheStack() {
        List<TrackPoint> simplified = TrackSimplifier.simplify(straightLineOf(10_000), 5);

        assertThat(simplified).isNotEmpty().hasSizeLessThan(100);
    }

    /** Les points retenus gardent leur ordre : une trace inversée ne se dessine pas. */
    @Test
    void whatIsKeptStaysInOrder() {
        var points = new java.util.ArrayList<>(straightLineOf(20));
        points.add(10, aPoint().sequence(99).at(new com.runtrack.shared.measure.GeoPoint(
                50.6302, 3.0573)).secondsAfterStart(10).build());

        List<TrackPoint> simplified = TrackSimplifier.simplify(points, 5);

        assertThat(simplified).isSortedAccordingTo(
                java.util.Comparator.comparing(TrackPoint::recordedAt));
    }
}
