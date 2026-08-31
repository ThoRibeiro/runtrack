package com.runtrack.course.usecases.model.track;

import com.runtrack.course.usecases.model.activity.ActivityType;
import static com.runtrack.course.usecases.model.fixture.TrackPointBuilder.START;
import static com.runtrack.course.usecases.model.fixture.TrackPointBuilder.aPoint;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class TrackPointFilterTest {

    private static TrackPointFilter.Context contextAfter(TrackPoint previous, int lastSequence) {
        return new TrackPointFilter.Context(
                Optional.ofNullable(previous), lastSequence, START, START.plusSeconds(3_600), ActivityType.RUN);
    }

    private static TrackPointFilter.Context freshContext() {
        return contextAfter(null, -1);
    }

    @Test
    void keepsAPlausiblePoint() {
        TrackPoint point = aPoint().sequence(1).secondsAfterStart(10).build();

        assertThat(TrackPointFilter.evaluate(point, freshContext())).isEmpty();
    }

    @Test
    void dropsAPointWhoseSequenceWasAlreadyApplied() {
        TrackPoint replayed = aPoint().sequence(5).secondsAfterStart(50).build();

        assertThat(TrackPointFilter.evaluate(replayed, contextAfter(null, 5)))
                .contains(PointRejection.DUPLICATE_SEQUENCE);
        assertThat(TrackPointFilter.evaluate(replayed, contextAfter(null, 9)))
                .contains(PointRejection.DUPLICATE_SEQUENCE);
    }

    @Test
    void dropsAPointTooImpreciseToPlace() {
        TrackPoint blurry = aPoint().sequence(1).secondsAfterStart(10)
                .accuracy(TrackPointFilter.MAX_ACCURACY_METERS + 0.1).build();

        assertThat(TrackPointFilter.evaluate(blurry, freshContext()))
                .contains(PointRejection.ACCURACY_TOO_LOW);
    }

    @Test
    void keepsAPointExactlyAtTheAccuracyLimit() {
        TrackPoint borderline = aPoint().sequence(1).secondsAfterStart(10)
                .accuracy(TrackPointFilter.MAX_ACCURACY_METERS).build();

        assertThat(TrackPointFilter.evaluate(borderline, freshContext())).isEmpty();
    }

    @Test
    void dropsAPointDatedBeforeTheActivityStarted() {
        TrackPoint tooEarly = aPoint().sequence(1).recordedAt(START.minusSeconds(1)).build();

        assertThat(TrackPointFilter.evaluate(tooEarly, freshContext()))
                .contains(PointRejection.TIMESTAMP_BEFORE_START);
    }

    @Test
    void dropsAPointDatedInTheFuture() {
        TrackPoint tooLate = aPoint().sequence(1)
                .recordedAt(START.plusSeconds(3_600).plus(TrackPointFilter.MAX_FUTURE_DRIFT).plusSeconds(1))
                .build();

        assertThat(TrackPointFilter.evaluate(tooLate, freshContext()))
                .contains(PointRejection.TIMESTAMP_IN_FUTURE);
    }

    @Test
    void toleratesTheResidualClockDrift() {
        TrackPoint slightlyAhead = aPoint().sequence(1)
                .recordedAt(START.plusSeconds(3_600).plus(TrackPointFilter.MAX_FUTURE_DRIFT))
                .build();

        assertThat(TrackPointFilter.evaluate(slightlyAhead, freshContext())).isEmpty();
    }

    /** Un saut GPS à l'entrée d'un tunnel : cent mètres en une seconde, à pied. */
    @Test
    void dropsAPointImplyingAnImpossibleSpeed() {
        TrackPoint previous = aPoint().sequence(1).secondsAfterStart(10).metersEast(0).build();
        TrackPoint jump = aPoint().sequence(2).secondsAfterStart(11).metersEast(100).build();

        assertThat(TrackPointFilter.evaluate(jump, contextAfter(previous, 1)))
                .contains(PointRejection.IMPLAUSIBLE_SPEED);
    }

    /** Le même déplacement est parfaitement banal à vélo : le seuil suit le type d'activité. */
    @Test
    void judgesSpeedAgainstTheActivityType() {
        TrackPoint previous = aPoint().sequence(1).secondsAfterStart(10).metersEast(0).build();
        TrackPoint fast = aPoint().sequence(2).secondsAfterStart(15).metersEast(100).build();

        var onFoot = new TrackPointFilter.Context(
                Optional.of(previous), 1, START, START.plusSeconds(3_600), ActivityType.RUN);
        var onABike = new TrackPointFilter.Context(
                Optional.of(previous), 1, START, START.plusSeconds(3_600), ActivityType.BIKE);

        assertThat(TrackPointFilter.evaluate(fast, onFoot)).contains(PointRejection.IMPLAUSIBLE_SPEED);
        assertThat(TrackPointFilter.evaluate(fast, onABike)).isEmpty();
    }

    /** Deux points au même instant : aucune vitesse n'est calculable, on ne juge pas. */
    @Test
    void doesNotJudgeSpeedWithoutElapsedTime() {
        TrackPoint previous = aPoint().sequence(1).secondsAfterStart(10).metersEast(0).build();
        TrackPoint sameInstant = aPoint().sequence(2).secondsAfterStart(10).metersEast(100).build();

        assertThat(TrackPointFilter.evaluate(sameInstant, contextAfter(previous, 1))).isEmpty();
    }

    /** Idem si les horodatages reculent : pas de durée, donc pas de vitesse à juger. */
    @Test
    void doesNotJudgeSpeedWhenTimestampsGoBackwards() {
        TrackPoint previous = aPoint().sequence(1).secondsAfterStart(20).metersEast(0).build();
        TrackPoint earlier = aPoint().sequence(2).secondsAfterStart(10).metersEast(100).build();

        assertThat(TrackPointFilter.evaluate(earlier, contextAfter(previous, 1))).isEmpty();
    }

    @Test
    void refusesAnIncompleteContext() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TrackPointFilter.Context(
                null, -1, START, START, ActivityType.RUN));
        assertThatIllegalArgumentException().isThrownBy(() -> new TrackPointFilter.Context(
                Optional.empty(), -1, null, START, ActivityType.RUN));
        assertThatIllegalArgumentException().isThrownBy(() -> new TrackPointFilter.Context(
                Optional.empty(), -1, START, null, ActivityType.RUN));
        assertThatIllegalArgumentException().isThrownBy(() -> new TrackPointFilter.Context(
                Optional.empty(), -1, START, START, null));
    }
}
