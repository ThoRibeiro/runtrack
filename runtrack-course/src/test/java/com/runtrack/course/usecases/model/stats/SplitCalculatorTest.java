package com.runtrack.course.usecases.model.stats;

import com.runtrack.course.usecases.model.track.TrackPoint;
import static com.runtrack.course.usecases.model.fixture.TrackPointBuilder.aPoint;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.runtrack.shared.measure.Distance;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.Test;

class SplitCalculatorTest {

    /** Une trace régulière : un point par seconde, cinq mètres à chaque fois. */
    private static List<TrackPoint> steadyRun(int seconds, double metersPerSecond) {
        var points = new ArrayList<TrackPoint>();
        for (int second = 0; second <= seconds; second++) {
            points.add(aPoint().sequence(second).secondsAfterStart(second)
                    .metersEast(second * metersPerSecond).heartRate(150).build());
        }
        return points;
    }

    @Test
    void producesNothingBelowTwoPoints() {
        assertThat(SplitCalculator.byKilometer(List.of())).isEmpty();
        assertThat(SplitCalculator.byKilometer(List.of(aPoint().build()))).isEmpty();
    }

    @Test
    void cutsEveryKilometer() {
        List<Split> splits = SplitCalculator.byKilometer(steadyRun(600, 5));

        assertThat(splits).hasSize(3);
        assertThat(splits).extracting(Split::kilometerIndex).containsExactly(1, 2, 3);
    }

    /** À 5 m/s, chaque kilomètre prend 200 s. Le franchissement est interpolé, pas arrondi. */
    @Test
    void timesEachKilometerByInterpolatingTheCrossing() {
        List<Split> splits = SplitCalculator.byKilometer(steadyRun(600, 5));

        assertThat(splits.get(0).time().toSeconds()).isCloseTo(200, org.assertj.core.data.Offset.offset(1L));
        assertThat(splits.get(1).time().toSeconds()).isCloseTo(200, org.assertj.core.data.Offset.offset(1L));
    }

    @Test
    void marksCompleteAndPartialSplits() {
        List<Split> splits = SplitCalculator.byKilometer(steadyRun(300, 5));

        assertThat(splits).hasSize(2);
        assertThat(splits.get(0).isComplete()).isTrue();
        assertThat(splits.get(1).isComplete()).isFalse();
        assertThat(splits.get(1).distance().meters()).isCloseTo(500, Percentage.withPercentage(2));
    }

    @Test
    void computesThePaceOfEachSplit() {
        List<Split> splits = SplitCalculator.byKilometer(steadyRun(600, 5));

        assertThat(splits.get(0).pace()).isPresent();
        assertThat(splits.get(0).pace().orElseThrow().perKilometer().toSeconds())
                .isCloseTo(200, org.assertj.core.data.Offset.offset(2L));
    }

    @Test
    void averagesTheHeartRateWithinEachSplit() {
        List<Split> splits = SplitCalculator.byKilometer(steadyRun(400, 5));

        assertThat(splits.getFirst().averageHeartRate()).hasValue(150.0);
    }

    @Test
    void reportsNoHeartRateWhenNoneWasRecorded() {
        var points = new ArrayList<TrackPoint>();
        for (int second = 0; second <= 300; second++) {
            points.add(aPoint().sequence(second).secondsAfterStart(second).metersEast(second * 5.0).build());
        }

        assertThat(SplitCalculator.byKilometer(points).getFirst().averageHeartRate()).isEmpty();
    }

    @Test
    void accumulatesElevationWithinEachSplit() {
        var points = new ArrayList<TrackPoint>();
        for (int second = 0; second <= 400; second++) {
            points.add(aPoint().sequence(second).secondsAfterStart(second)
                    .metersEast(second * 5.0).elevation(100 + second).build());
        }

        List<Split> splits = SplitCalculator.byKilometer(points);

        assertThat(splits.getFirst().elevationGain()).isGreaterThan(150);
    }

    /** Un très long segment sans point intermédiaire referme plusieurs splits d'un coup. */
    @Test
    void closesSeveralKilometersOnASingleSegment() {
        var jump = List.of(
                aPoint().sequence(0).secondsAfterStart(0).metersEast(0).build(),
                aPoint().sequence(1).secondsAfterStart(600).metersEast(3_000).build());

        List<Split> splits = SplitCalculator.byKilometer(jump);

        assertThat(splits).hasSize(3);
        assertThat(splits.get(0).isComplete()).isTrue();
        assertThat(splits.get(1).isComplete()).isTrue();
        assertThat(splits.get(0).distance()).isEqualTo(Distance.ofKilometers(1));
        assertThat(splits.get(1).distance()).isEqualTo(Distance.ofKilometers(1));
        assertThat(splits.get(0).time().plus(splits.get(1).time()).plus(splits.get(2).time()))
                .isEqualTo(Duration.ofSeconds(600));
    }

    @Test
    void ignoresPointsThatDoNotMove() {
        var stationary = new ArrayList<TrackPoint>();
        for (int second = 0; second <= 100; second++) {
            stationary.add(aPoint().sequence(second).secondsAfterStart(second).metersEast(0).build());
        }

        assertThat(SplitCalculator.byKilometer(stationary)).isEmpty();
    }

    @Test
    void numbersSplitsFromOne() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Split(0, Distance.ZERO, Duration.ZERO, Optional.empty(), 0, OptionalDouble.empty()))
                .withMessageContaining("à partir de 1");
    }
}
