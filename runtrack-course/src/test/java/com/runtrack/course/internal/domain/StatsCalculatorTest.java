package com.runtrack.course.internal.domain;

import static com.runtrack.course.internal.domain.TrackPointBuilder.aPoint;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.runtrack.shared.Distance;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.Test;

class StatsCalculatorTest {

    private static StatsAccumulator accumulate(List<TrackPoint> points) {
        StatsAccumulator accumulator = StatsAccumulator.empty();
        for (TrackPoint point : points) {
            accumulator = accumulator.apply(point);
        }
        return accumulator;
    }

    /** Une minute à 5 m/s, soit 3'20" au kilomètre. */
    private static List<TrackPoint> aSteadyMinute() {
        var points = new ArrayList<TrackPoint>();
        for (int second = 0; second <= 60; second++) {
            points.add(aPoint().sequence(second).secondsAfterStart(second)
                    .metersEast(second * 5.0).heartRate(150).build());
        }
        return points;
    }

    @Test
    void derivesAveragePaceFromMovingTime() {
        ActivityStats stats = StatsCalculator.summarize(
                accumulate(aSteadyMinute()), Duration.ofMinutes(1), ActivityType.RUN, Optional.empty());

        assertThat(stats.averagePace()).isPresent();
        assertThat(stats.averagePace().orElseThrow().perKilometer().toSeconds())
                .isCloseTo(200, Percentage.withPercentage(2));
    }

    @Test
    void hasNoPaceBeforeAnythingMoved() {
        ActivityStats stats = StatsCalculator.summarize(
                StatsAccumulator.empty(), Duration.ZERO, ActivityType.RUN, Optional.empty());

        assertThat(stats.averagePace()).isEmpty();
        assertThat(stats.currentPace()).isEmpty();
        assertThat(stats.distance()).isEqualTo(Distance.ZERO);
    }

    @Test
    void hasNoCurrentPaceWithASingleWindowPoint() {
        ActivityStats stats = StatsCalculator.summarize(
                StatsAccumulator.empty().apply(aPoint().sequence(0).build()),
                Duration.ofSeconds(1), ActivityType.RUN, Optional.empty());

        assertThat(stats.currentPace()).isEmpty();
    }

    /** L'allure instantanée suit l'effort du moment, pas la moyenne de la sortie. */
    @Test
    void currentPaceReactsToTheLastThirtySeconds() {
        var points = new ArrayList<TrackPoint>();
        for (int second = 0; second <= 60; second++) {
            double speed = second < 30 ? 2.0 : 6.0;
            double travelled = second < 30 ? second * 2.0 : 60 + (second - 30) * speed;
            points.add(aPoint().sequence(second).secondsAfterStart(second).metersEast(travelled).build());
        }

        ActivityStats stats = StatsCalculator.summarize(
                accumulate(points), Duration.ofMinutes(1), ActivityType.RUN, Optional.empty());

        double currentSpeed = stats.currentPace().orElseThrow().metersPerSecond();
        double averageSpeed = stats.averagePace().orElseThrow().metersPerSecond();
        assertThat(currentSpeed).isGreaterThan(averageSpeed);
        assertThat(currentSpeed).isCloseTo(6.0, Percentage.withPercentage(5));
    }

    @Test
    void averagesTheHeartRateOverItsSamples() {
        ActivityStats stats = StatsCalculator.summarize(
                accumulate(aSteadyMinute()), Duration.ofMinutes(1), ActivityType.RUN, Optional.empty());

        assertThat(stats.averageHeartRate()).hasValue(150.0);
        assertThat(stats.maxHeartRate()).hasValue(150);
    }

    @Test
    void reportsNoHeartRateWithoutASingleReading() {
        var points = List.of(
                aPoint().sequence(0).secondsAfterStart(0).metersEast(0).build(),
                aPoint().sequence(1).secondsAfterStart(1).metersEast(5).build());

        ActivityStats stats = StatsCalculator.summarize(
                accumulate(points), Duration.ofSeconds(1), ActivityType.RUN, Optional.empty());

        assertThat(stats.averageHeartRate()).isEmpty();
        assertThat(stats.maxHeartRate()).isEmpty();
    }

    @Test
    void carriesTheElevationTotals() {
        var points = List.of(
                aPoint().sequence(0).secondsAfterStart(0).metersEast(0).elevation(100).build(),
                aPoint().sequence(1).secondsAfterStart(1).metersEast(5).elevation(150).build(),
                aPoint().sequence(2).secondsAfterStart(2).metersEast(10).elevation(120).build());

        ActivityStats stats = StatsCalculator.summarize(
                accumulate(points), Duration.ofSeconds(2), ActivityType.RUN, Optional.empty());

        assertThat(stats.elevationGain()).isCloseTo(50, org.assertj.core.data.Offset.offset(0.01));
        assertThat(stats.elevationLoss()).isCloseTo(30, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void keepsElapsedAndMovingTimeDistinct() {
        var standingStill = new ArrayList<TrackPoint>();
        for (int second = 0; second <= 30; second++) {
            standingStill.add(aPoint().sequence(second).secondsAfterStart(second).metersEast(0).build());
        }

        ActivityStats stats = StatsCalculator.summarize(
                accumulate(standingStill), Duration.ofMinutes(5), ActivityType.RUN, Optional.empty());

        assertThat(stats.elapsed()).isEqualTo(Duration.ofMinutes(5));
        assertThat(stats.movingTime()).isZero();
    }

    @Test
    void refusesIncompleteStats() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ActivityStats(
                null, Duration.ZERO, Duration.ZERO, Optional.empty(), Optional.empty(), 0, 0,
                Optional.empty(), Optional.empty(), OptionalDouble.empty(), OptionalInt.empty(), OptionalInt.empty()));
        assertThatIllegalArgumentException().isThrownBy(() -> new ActivityStats(
                Distance.ZERO, null, Duration.ZERO, Optional.empty(), Optional.empty(), 0, 0,
                Optional.empty(), Optional.empty(), OptionalDouble.empty(), OptionalInt.empty(), OptionalInt.empty()));
        assertThatIllegalArgumentException().isThrownBy(() -> new ActivityStats(
                Distance.ZERO, Duration.ZERO, null, Optional.empty(), Optional.empty(), 0, 0,
                Optional.empty(), Optional.empty(), OptionalDouble.empty(), OptionalInt.empty(), OptionalInt.empty()));
    }
}
