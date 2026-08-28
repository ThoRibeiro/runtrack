package com.runtrack.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GeoPointTest {

    @Test
    void acceptsAValidPosition() {
        GeoPoint lille = new GeoPoint(50.6292, 3.0573);

        assertThat(lille.latitude()).isEqualTo(50.6292);
        assertThat(lille.longitude()).isEqualTo(3.0573);
    }

    @ParameterizedTest
    @CsvSource({"-90, -180", "90, 180", "0, 0"})
    void acceptsTheBoundaries(double latitude, double longitude) {
        assertThat(new GeoPoint(latitude, longitude)).isNotNull();
    }

    @ParameterizedTest
    @CsvSource({"-90.1, 0", "90.1, 0"})
    void refusesLatitudeOutOfRange(double latitude, double longitude) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GeoPoint(latitude, longitude))
                .withMessageContaining("Latitude");
    }

    @ParameterizedTest
    @CsvSource({"0, -180.1", "0, 180.1"})
    void refusesLongitudeOutOfRange(double latitude, double longitude) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GeoPoint(latitude, longitude))
                .withMessageContaining("Longitude");
    }

    @ParameterizedTest
    @CsvSource({"NaN, 0", "0, NaN"})
    void refusesNonNumericCoordinates(double latitude, double longitude) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GeoPoint(latitude, longitude))
                .withMessageContaining("non numériques");
    }
}
