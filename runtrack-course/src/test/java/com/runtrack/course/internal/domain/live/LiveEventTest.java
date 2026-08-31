package com.runtrack.course.internal.domain.live;

import static com.runtrack.course.internal.domain.fixture.TrackPointBuilder.aPoint;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.runtrack.course.internal.domain.track.TrackPoint;
import com.runtrack.shared.measure.Elevation;
import com.runtrack.shared.measure.GeoPoint;
import java.time.Instant;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class LiveEventTest {

    private static final Instant AT = Instant.parse("2026-08-29T08:00:30Z");
    private static final GeoPoint LILLE = new GeoPoint(50.6292, 3.0573);

    /**
     * Le nom sert de type d'événement SSE. Le figer dans un test, c'est empêcher qu'un
     * renommage anodin côté serveur casse tous les clients d'un coup.
     */
    @Test
    void eachEventNamesItsOwnKind() {
        assertThat(new LiveEvent.Status("Live", AT).kind()).isEqualTo("status");
        assertThat(new LiveEvent.Position(1, LILLE, Elevation.SEA_LEVEL, AT, OptionalInt.empty()).kind())
                .isEqualTo("position");
    }

    /** Une position ne retient du point que ce qui s'affiche : ni précision, ni cadence. */
    @Test
    void aPositionKeepsOnlyWhatIsDrawn() {
        TrackPoint point = aPoint().sequence(4).accuracy(12).cadence(180).heartRate(150).build();

        LiveEvent.Position position = LiveEvent.Position.of(point);

        assertThat(position.sequenceNumber()).isEqualTo(4);
        assertThat(position.heartRate()).hasValue(150);
        assertThat(position.recordedAt()).isEqualTo(point.recordedAt());
    }

    @Test
    void anIncompletePositionIsRefused() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(
                () -> new LiveEvent.Position(1, null, Elevation.SEA_LEVEL, AT, OptionalInt.empty()));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(
                () -> new LiveEvent.Position(1, LILLE, Elevation.SEA_LEVEL, AT, null));
    }

    @Test
    void statsWithoutStatsMakeNoSense() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new LiveEvent.Stats(null));
    }

    @Test
    void aTransitionWithoutStatusOrInstantIsRefused() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new LiveEvent.Status(" ", AT));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new LiveEvent.Status("Live", null));
    }
}
