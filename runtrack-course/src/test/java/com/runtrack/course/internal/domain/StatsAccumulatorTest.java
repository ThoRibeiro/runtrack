package com.runtrack.course.internal.domain;

import static com.runtrack.course.internal.domain.TrackPointBuilder.aPoint;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.Test;

class StatsAccumulatorTest {

    /** Une minute de course à 5 m/s, un point par seconde. */
    private static List<TrackPoint> aSteadyMinute() {
        var points = new ArrayList<TrackPoint>();
        for (int second = 0; second <= 60; second++) {
            points.add(aPoint().sequence(second).secondsAfterStart(second)
                    .metersEast(second * 5.0).elevation(20).build());
        }
        return points;
    }

    private static StatsAccumulator accumulate(List<TrackPoint> points) {
        StatsAccumulator accumulator = StatsAccumulator.empty();
        for (TrackPoint point : points) {
            accumulator = accumulator.apply(point);
        }
        return accumulator;
    }

    @Test
    void startsEmpty() {
        StatsAccumulator empty = StatsAccumulator.empty();

        assertThat(empty.distance()).isEqualTo(com.runtrack.shared.Distance.ZERO);
        assertThat(empty.movingTime()).isZero();
        assertThat(empty.lastPoint()).isEmpty();
        assertThat(empty.firstPoint()).isEmpty();
        assertThat(empty.recentWindow()).isEmpty();
        assertThat(empty.lastAppliedSequence()).isNegative();
    }

    @Test
    void theFirstPointOpensTheTrackWithoutAddingDistance() {
        StatsAccumulator accumulator = StatsAccumulator.empty().apply(aPoint().sequence(0).build());

        assertThat(accumulator.distance().isZero()).isTrue();
        assertThat(accumulator.movingTime()).isZero();
        assertThat(accumulator.firstPoint()).isPresent();
        assertThat(accumulator.lastAppliedSequence()).isZero();
    }

    @Test
    void addsDistanceAndMovingTimeSegmentBySegment() {
        StatsAccumulator accumulator = accumulate(aSteadyMinute());

        assertThat(accumulator.distance().meters()).isCloseTo(300, Percentage.withPercentage(1));
        assertThat(accumulator.movingTime()).isEqualTo(Duration.ofSeconds(60));
        assertThat(accumulator.lastAppliedSequence()).isEqualTo(60);
    }

    /** Debout à un feu rouge : le temps passe, le temps en mouvement non. */
    @Test
    void doesNotCountTimeSpentStandingStill() {
        var points = new ArrayList<TrackPoint>();
        for (int second = 0; second <= 30; second++) {
            points.add(aPoint().sequence(second).secondsAfterStart(second).metersEast(0).build());
        }

        StatsAccumulator accumulator = accumulate(points);

        assertThat(accumulator.movingTime()).isZero();
        assertThat(accumulator.distance().meters()).isZero();
    }

    @Test
    void tracksHeartRateAndAltitudeExtremes() {
        StatsAccumulator accumulator = accumulate(List.of(
                aPoint().sequence(0).secondsAfterStart(0).metersEast(0).elevation(100).heartRate(120).build(),
                aPoint().sequence(1).secondsAfterStart(1).metersEast(5).elevation(140).heartRate(160).build(),
                aPoint().sequence(2).secondsAfterStart(2).metersEast(10).elevation(90).heartRate(150).build()));

        assertThat(accumulator.heartRateSamples()).isEqualTo(3);
        assertThat(accumulator.heartRateSum()).isEqualTo(430);
        assertThat(accumulator.maxHeartRate()).hasValue(160);
        assertThat(accumulator.minAltitude()).map(com.runtrack.shared.Elevation::meters).contains(90.0);
        assertThat(accumulator.maxAltitude()).map(com.runtrack.shared.Elevation::meters).contains(140.0);
    }

    @Test
    void ignoresPointsWithoutAHeartRateReading() {
        StatsAccumulator accumulator = accumulate(List.of(
                aPoint().sequence(0).secondsAfterStart(0).metersEast(0).heartRate(140).build(),
                aPoint().sequence(1).secondsAfterStart(1).metersEast(5).build()));

        assertThat(accumulator.heartRateSamples()).isEqualTo(1);
        assertThat(accumulator.maxHeartRate()).hasValue(140);
    }

    @Test
    void keepsTheSlidingWindowBounded() {
        StatsAccumulator accumulator = accumulate(aSteadyMinute());

        Duration span = Duration.between(
                accumulator.recentWindow().getFirst().recordedAt(),
                accumulator.recentWindow().getLast().recordedAt());

        assertThat(span).isLessThanOrEqualTo(StatsAccumulator.INSTANT_PACE_WINDOW);
        assertThat(accumulator.recentWindow()).hasSizeLessThan(aSteadyMinute().size());
    }

    @Test
    void ignoresAnAlreadyAppliedSequenceNumber() {
        TrackPoint first = aPoint().sequence(5).secondsAfterStart(5).metersEast(0).build();
        TrackPoint replayed = aPoint().sequence(5).secondsAfterStart(5).metersEast(500).build();

        StatsAccumulator once = StatsAccumulator.empty().apply(first);
        StatsAccumulator twice = once.apply(replayed);

        assertThat(twice).isEqualTo(once);
    }

    /**
     * La propriété qui rend l'ingestion réellement idempotente, et la seule qui le prouve :
     * rejouer un lot entier — ou l'intégralité de la course — ne bouge pas une statistique.
     * Sans elle, l'accumulateur incrémental et l'exigence de rejeu se contrediraient.
     */
    @Test
    void replayingABatchChangesNothing() {
        List<TrackPoint> minute = aSteadyMinute();
        List<TrackPoint> firstBatch = minute.subList(0, 30);
        List<TrackPoint> secondBatch = minute.subList(30, minute.size());

        StatsAccumulator straight = accumulate(minute);

        StatsAccumulator withReplays = StatsAccumulator.empty();
        for (TrackPoint point : firstBatch) {
            withReplays = withReplays.apply(point);
        }
        for (TrackPoint point : firstBatch) {
            withReplays = withReplays.apply(point);
        }
        for (TrackPoint point : secondBatch) {
            withReplays = withReplays.apply(point);
        }
        for (TrackPoint point : secondBatch) {
            withReplays = withReplays.apply(point);
        }

        assertThat(withReplays).isEqualTo(straight);
    }

    /**
     * Le corollaire : l'accumulateur est une fonction déterministe de la suite des points
     * acceptés. Rejouer depuis zéro doit redonner exactement le même état, sinon la
     * reconstruction après incident produirait d'autres chiffres que le direct.
     */
    @Test
    void replayingFromScratchRebuildsTheSameState() {
        List<TrackPoint> minute = aSteadyMinute();

        assertThat(accumulate(minute)).isEqualTo(accumulate(minute));
    }

    /**
     * Deux points partageant le même horodatage, ou datés à rebours : la distance compte,
     * mais aucun temps en mouvement ne peut en être tiré sans inventer une vitesse infinie.
     */
    @Test
    void addsNoMovingTimeWhenTimeDidNotAdvance() {
        StatsAccumulator sameInstant = StatsAccumulator.empty()
                .apply(aPoint().sequence(0).secondsAfterStart(10).metersEast(0).build())
                .apply(aPoint().sequence(1).secondsAfterStart(10).metersEast(50).build());

        assertThat(sameInstant.movingTime()).isZero();
        assertThat(sameInstant.distance().meters()).isCloseTo(50, Percentage.withPercentage(1));

        StatsAccumulator goingBackwards = StatsAccumulator.empty()
                .apply(aPoint().sequence(0).secondsAfterStart(20).metersEast(0).build())
                .apply(aPoint().sequence(1).secondsAfterStart(10).metersEast(50).build());

        assertThat(goingBackwards.movingTime()).isZero();
    }

    @Test
    void isImmutable() {
        List<TrackPoint> minute = aSteadyMinute();
        StatsAccumulator before = accumulate(minute.subList(0, 10));

        StatsAccumulator after = before.apply(minute.get(10));

        assertThat(before.lastAppliedSequence()).isEqualTo(9);
        assertThat(after.lastAppliedSequence()).isEqualTo(10);
        assertThat(before.distance()).isNotEqualTo(after.distance());
    }
}
