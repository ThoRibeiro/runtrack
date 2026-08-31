package com.runtrack.course.usecases.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.runtrack.course.usecases.model.activity.ActivityType;
import com.runtrack.course.usecases.model.stats.ActivityStats;
import com.runtrack.course.usecases.model.stats.StatsAccumulator;
import com.runtrack.course.usecases.model.stats.StatsCalculator;
import com.runtrack.course.usecases.model.track.TrackPoint;
import com.runtrack.shared.error.ConflictException;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * Deux lots concurrents sur la même course : le verrou optimiste les détecte, et le client
 * ne doit pas en entendre parler tant que la reprise suffit.
 */
class ResilientPointIngestionTest {

    private static final UserId MARIE = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000001"));
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-29T08:00:00Z"), ZoneOffset.UTC);
    private static final ActivityId RUN = ActivityId.generate(CLOCK, new Random(5));

    /**
     * Échoue les {@code failures} premières fois, puis rend un résultat.
     *
     * <p>Sous-classe plutôt que double d'un port : la reprise entoure un <em>cas d'usage</em>,
     * pas une dépendance sortante, et introduire une interface pour la seule commodité du test
     * ajouterait un type que la production n'utiliserait jamais.
     */
    private static final class FlakyIngestion extends PointIngestion {

        private final int failures;
        private int attempts;

        FlakyIngestion(int failures) {
            super(null, null, null, null, null, CLOCK,
                    new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
            this.failures = failures;
        }

        @Override
        public IngestionResult ingest(UserId ownerId, ActivityId activityId, List<TrackPoint> batch) {
            if (++attempts <= failures) {
                throw new OptimisticLockingFailureException("activity_stats");
            }
            return new IngestionResult(emptyStats(), 0, 0, List.of());
        }
    }

    private static ActivityStats emptyStats() {
        return StatsCalculator.summarize(
                StatsAccumulator.empty(), Duration.ZERO, ActivityType.RUN, Optional.empty());
    }

    @Test
    void aSingleConflictIsRetriedWithoutTheClientKnowing() {
        var flaky = new FlakyIngestion(1);

        IngestionResult result = new ResilientPointIngestion(flaky).ingest(MARIE, RUN, List.of());

        assertThat(result.acceptedCount()).isZero();
        assertThat(flaky.attempts).isEqualTo(2);
    }

    @Test
    void aBatchThatNeverGetsThroughEndsAsAConflict() {
        var hopeless = new FlakyIngestion(Integer.MAX_VALUE);
        var resilient = new ResilientPointIngestion(hopeless);

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> resilient.ingest(MARIE, RUN, List.of()))
                .satisfies(conflict -> {
                    assertThat(conflict.code()).isEqualTo("INGESTION_CONFLICT");
                    // La cause est conservée : sans elle, l'incident est indiagnosticable.
                    assertThat(conflict).hasCauseInstanceOf(OptimisticLockingFailureException.class);
                });
        assertThat(hopeless.attempts).isEqualTo(3);
    }

    @Test
    void aBatchThatPassesFirstTimeIsNotRetried() {
        var steady = new FlakyIngestion(0);

        new ResilientPointIngestion(steady).ingest(MARIE, RUN, List.of());

        assertThat(steady.attempts).isEqualTo(1);
    }
}
