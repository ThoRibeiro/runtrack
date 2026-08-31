package com.runtrack.course.infrastructure.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.runtrack.course.usecases.model.live.LiveEvent;
import com.runtrack.course.usecases.model.stats.ActivityStats;
import com.runtrack.course.usecases.model.stats.StatsAccumulator;
import com.runtrack.course.usecases.model.stats.StatsCalculator;
import com.runtrack.course.usecases.model.activity.ActivityType;
import com.runtrack.platform.realtime.PublishedEvent;
import com.runtrack.shared.measure.Elevation;
import com.runtrack.shared.measure.GeoPoint;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class LiveEventCodecTest {

    private static final Instant AT = Instant.parse("2026-08-29T08:00:30Z");

    private final LiveEventCodec codec = new LiveEventCodec(new ObjectMapper());

    @Test
    void aPositionCarriesWhatIsDisplayedAndNothingElse() {
        var position = new LiveEvent.Position(
                7, new GeoPoint(50.6292, 3.0573), Elevation.ofMeters(20), AT, OptionalInt.of(142));

        PublishedEvent encoded = codec.encode(position);

        assertThat(encoded.kind()).isEqualTo("position");
        assertThat(encoded.payload())
                .contains("\"sequenceNumber\":7")
                .contains("\"heartRate\":142")
                // Ni la précision ni la cadence : elles servent au filtrage, pas à l'affichage.
                .doesNotContain("accuracy")
                .doesNotContain("cadence");
    }

    @Test
    void anAbsentHeartRateIsOmittedRatherThanSentAsZero() {
        var position = new LiveEvent.Position(
                1, new GeoPoint(50.6292, 3.0573), Elevation.ofMeters(20), AT, OptionalInt.empty());

        assertThat(codec.encode(position).payload()).doesNotContain("heartRate");
    }

    @Test
    void statsUseTheSameShapeAsTheRestReading() {
        ActivityStats stats = StatsCalculator.summarize(
                StatsAccumulator.empty(), Duration.ofMinutes(2), ActivityType.RUN, Optional.empty());

        PublishedEvent encoded = codec.encode(new LiveEvent.Stats(stats));

        assertThat(encoded.kind()).isEqualTo("stats");
        assertThat(encoded.payload())
                .contains("\"distanceMeters\"")
                .contains("\"elapsedSeconds\":120");
    }

    @Test
    void aStatusCarriesItsTransitionInstant() {
        PublishedEvent encoded = codec.encode(new LiveEvent.Status("Paused", AT));

        assertThat(encoded.kind()).isEqualTo("status");
        assertThat(encoded.payload()).contains("\"status\":\"Paused\"");
    }

    /** Un événement encodé n'est pas encore une entrée du journal : il n'a pas d'identifiant. */
    @Test
    void anEncodedEventCarriesNoStreamIdentifierYet() {
        assertThat(codec.encode(new LiveEvent.Status("Live", AT)).eventId()).isNull();
    }
}
