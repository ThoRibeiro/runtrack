package com.runtrack.course.usecases.model.track;

import static com.runtrack.course.usecases.model.fixture.TrackPointBuilder.aPoint;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.runtrack.shared.measure.Elevation;
import com.runtrack.shared.measure.GeoPoint;
import java.time.Instant;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class TrackPointTest {

    @Test
    void carriesTheOptionalSensorReadings() {
        TrackPoint point = aPoint().heartRate(152).cadence(178).build();

        assertThat(point.heartRate()).hasValue(152);
        assertThat(point.cadence()).hasValue(178);
    }

    @Test
    void toleratesMissingSensors() {
        TrackPoint point = aPoint().build();

        assertThat(point.heartRate()).isEmpty();
        assertThat(point.cadence()).isEmpty();
    }

    @Test
    void refusesANegativeSequenceNumber() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> aPoint().sequence(-1).build())
                .withMessageContaining("sequenceNumber");
    }

    @Test
    void refusesAnInvalidAccuracy() {
        assertThatIllegalArgumentException().isThrownBy(() -> aPoint().accuracy(-1).build());
        assertThatIllegalArgumentException().isThrownBy(() -> aPoint().accuracy(Double.NaN).build());
    }

    @Test
    void refusesMissingEssentials() {
        Instant now = Instant.parse("2026-08-29T08:00:00Z");
        GeoPoint position = new GeoPoint(50.6292, 3.0573);
        Elevation elevation = Elevation.ofMeters(20);

        assertThatIllegalArgumentException().isThrownBy(() -> new TrackPoint(
                1, null, elevation, now, 5, OptionalInt.empty(), OptionalInt.empty()));
        assertThatIllegalArgumentException().isThrownBy(() -> new TrackPoint(
                1, position, null, now, 5, OptionalInt.empty(), OptionalInt.empty()));
        assertThatIllegalArgumentException().isThrownBy(() -> new TrackPoint(
                1, position, elevation, null, 5, OptionalInt.empty(), OptionalInt.empty()));
    }

    /** Un {@code null} là où on attend un OptionalInt est une erreur de programmation, pas une absence de mesure. */
    @Test
    void refusesNullInsteadOfAnEmptyOptional() {
        Instant now = Instant.parse("2026-08-29T08:00:00Z");
        GeoPoint position = new GeoPoint(50.6292, 3.0573);
        Elevation elevation = Elevation.ofMeters(20);

        assertThatIllegalArgumentException().isThrownBy(() -> new TrackPoint(
                1, position, elevation, now, 5, null, OptionalInt.empty()))
                .withMessageContaining("OptionalInt.empty()");
        assertThatIllegalArgumentException().isThrownBy(() -> new TrackPoint(
                1, position, elevation, now, 5, OptionalInt.empty(), null));
    }
}
