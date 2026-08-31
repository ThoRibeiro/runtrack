package com.runtrack.course.internal.infra.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.runtrack.course.internal.domain.live.LiveEvent;
import com.runtrack.course.internal.domain.stats.ActivityStats;
import com.runtrack.course.internal.domain.stats.StatsAccumulator;
import com.runtrack.course.internal.domain.stats.StatsCalculator;
import com.runtrack.course.internal.domain.activity.ActivityType;
import com.runtrack.shared.measure.Elevation;
import com.runtrack.shared.measure.GeoPoint;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
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

        Map<String, String> encoded = codec.encode(position);

        assertThat(encoded).containsEntry(LiveEventCodec.KIND_FIELD, "position");
        assertThat(encoded.get(LiveEventCodec.PAYLOAD_FIELD))
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

        assertThat(codec.encode(position).get(LiveEventCodec.PAYLOAD_FIELD)).doesNotContain("heartRate");
    }

    @Test
    void statsUseTheSameShapeAsTheRestReading() {
        ActivityStats stats = StatsCalculator.summarize(
                StatsAccumulator.empty(), Duration.ofMinutes(2), ActivityType.RUN, Optional.empty());

        Map<String, String> encoded = codec.encode(new LiveEvent.Stats(stats));

        assertThat(encoded).containsEntry(LiveEventCodec.KIND_FIELD, "stats");
        assertThat(encoded.get(LiveEventCodec.PAYLOAD_FIELD))
                .contains("\"distanceMeters\"")
                .contains("\"elapsedSeconds\":120");
    }

    @Test
    void aStatusCarriesItsTransitionInstant() {
        Map<String, String> encoded = codec.encode(new LiveEvent.Status("Paused", AT));

        assertThat(encoded).containsEntry(LiveEventCodec.KIND_FIELD, "status");
        assertThat(encoded.get(LiveEventCodec.PAYLOAD_FIELD)).contains("\"status\":\"Paused\"");
    }

    @Test
    void decodingRebuildsWhatEncodingWrote() {
        Map<String, String> encoded = codec.encode(new LiveEvent.Status("Live", AT));

        RecordedEvent decoded = LiveEventCodec.decode("1700000000000-0", encoded);

        assertThat(decoded.eventId()).isEqualTo("1700000000000-0");
        assertThat(decoded.kind()).isEqualTo("status");
        assertThat(decoded.payload()).isEqualTo(encoded.get(LiveEventCodec.PAYLOAD_FIELD));
    }

    /** Une entrée d'un format antérieur ne doit pas faire tomber la diffusion de toute la course. */
    @Test
    void anIncompleteEntryIsIgnoredRatherThanFatal() {
        assertThat(LiveEventCodec.decode("1-0", Map.of("kind", "position"))).isNull();
        assertThat(LiveEventCodec.decode("1-0", Map.of())).isNull();
    }

    @Test
    void anEventSentOverSseCarriesNoStreamIdentifier() {
        RecordedEvent event = codec.encodeForSse(new LiveEvent.Status("Live", AT));

        assertThat(event.eventId()).isNull();
        assertThat(event.kind()).isEqualTo("status");
    }
}
