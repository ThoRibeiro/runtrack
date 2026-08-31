package com.runtrack.course.internal.infra.rest;

import static com.runtrack.course.internal.domain.fixture.TrackPointBuilder.START;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.runtrack.course.internal.application.ActivityLifecycle;
import com.runtrack.course.internal.application.ActivityQueries;
import com.runtrack.course.internal.application.PointIngestion;
import com.runtrack.course.internal.application.ResilientPointIngestion;
import com.runtrack.course.internal.application.fixture.CourseDoubles;
import com.runtrack.course.internal.domain.activity.Activity;
import com.runtrack.course.internal.domain.activity.ActivityType;
import com.runtrack.course.internal.infra.rest.dto.PointDtos;
import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.error.ConflictException;
import com.runtrack.shared.id.UserId;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

/** Le premier niveau d'idempotence : rejouer une requête doit rendre <em>la même</em> réponse. */
class IdempotentIngestionTest {

    private static final UserId MARIE = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000001"));
    private static final Clock AT_START = Clock.fixed(START, ZoneOffset.UTC);
    private static final Clock AN_HOUR_LATER = Clock.fixed(START.plusSeconds(3_600), ZoneOffset.UTC);

    private CourseDoubles.Points points;
    private CourseDoubles.IdempotencyKeys keys;
    private ActivityLifecycle lifecycle;
    private IdempotentIngestion idempotent;

    @BeforeEach
    void setUp() {
        var activities = new CourseDoubles.Activities();
        var stats = new CourseDoubles.Stats();
        var relations = new CourseDoubles.Relations();
        var users = new CourseDoubles.Users();
        points = new CourseDoubles.Points();
        keys = new CourseDoubles.IdempotencyKeys();
        ApplicationEventPublisher publisher = event -> { };

        var live = new CourseDoubles.LivePublisher();
        lifecycle = new ActivityLifecycle(
                activities, stats, relations, publisher, live, AT_START, new Random(7));
        var queries = new ActivityQueries(activities, stats, relations, users, AN_HOUR_LATER);
        var ingestion = new PointIngestion(activities, stats, points, queries, live, AN_HOUR_LATER);
        idempotent = new IdempotentIngestion(
                new ResilientPointIngestion(ingestion), keys, new ObjectMapper());
    }

    private Activity aRun() {
        return lifecycle.start(MARIE, ActivityType.RUN, "Sortie", null, AudienceScope.PUBLIC, null);
    }

    private static PointDtos.IngestPointsRequest aBatch(int count) {
        double degreesPerMeter = 1 / (111_320d * Math.cos(Math.toRadians(50.6292)));
        return new PointDtos.IngestPointsRequest(IntStream.rangeClosed(1, count)
                .mapToObj(index -> new PointDtos.PointDto(
                        index,
                        50.6292,
                        3.0573 + 100d * index * degreesPerMeter,
                        20,
                        START.plusSeconds(30L * index),
                        5,
                        null,
                        null))
                .toList());
    }

    @Test
    void withoutAKeyNothingIsMemorised() {
        Activity run = aRun();

        String body = idempotent.ingest(MARIE, run.id(), aBatch(3), Optional.empty());

        assertThat(body).contains("\"acceptedCount\":3");
        assertThat(keys.writes()).isZero();
    }

    /**
     * La raison d'être de ce niveau : sans lui, le rejeu rendrait une réponse
     * <em>correcte</em> — trois doublons — mais différente, et le client croirait avoir perdu
     * ses points.
     */
    @Test
    void theSameKeyAndTheSameBatchReplayTheStoredResponse() {
        Activity run = aRun();
        PointDtos.IngestPointsRequest batch = aBatch(3);

        String first = idempotent.ingest(MARIE, run.id(), batch, Optional.of("buffer-42"));
        String replay = idempotent.ingest(MARIE, run.id(), batch, Optional.of("buffer-42"));

        assertThat(replay).isEqualTo(first).contains("\"acceptedCount\":3");
        assertThat(keys.writes()).isEqualTo(1);
        assertThat(points.count(run.id())).isEqualTo(3);
    }

    /** Deux clés distinctes : le second lot est une suite légitime, pas un rejeu. */
    @Test
    void anotherKeyRunsTheIngestionAgain() {
        Activity run = aRun();

        idempotent.ingest(MARIE, run.id(), aBatch(3), Optional.of("buffer-1"));
        String second = idempotent.ingest(MARIE, run.id(), aBatch(5), Optional.of("buffer-2"));

        assertThat(second).contains("\"acceptedCount\":2");
        assertThat(keys.writes()).isEqualTo(2);
        assertThat(points.count(run.id())).isEqualTo(5);
    }

    @Test
    void theSameKeyOnAnotherBatchIsAConflict() {
        Activity run = aRun();
        idempotent.ingest(MARIE, run.id(), aBatch(3), Optional.of("buffer-42"));

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> idempotent.ingest(MARIE, run.id(), aBatch(4), Optional.of("buffer-42")))
                .satisfies(conflict -> assertThat(conflict.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
    }

    /** La clé n'a de portée qu'au sein d'une course : deux courses peuvent réutiliser la même. */
    @Test
    void theSameKeyOnAnotherRunIsNotAConflict() {
        Activity first = aRun();
        Activity second = aRun();
        idempotent.ingest(MARIE, first.id(), aBatch(3), Optional.of("buffer-1"));

        String body = idempotent.ingest(MARIE, second.id(), aBatch(3), Optional.of("buffer-1"));

        assertThat(body).contains("\"acceptedCount\":3");
        assertThat(points.count(second.id())).isEqualTo(3);
    }

    @Test
    void aBlankKeyCountsAsNoKey() {
        Activity run = aRun();

        idempotent.ingest(MARIE, run.id(), aBatch(3), Optional.of("   "));

        assertThat(keys.writes()).isZero();
    }

    @Test
    void aKeyWiderThanItsColumnIsRefused() {
        Activity run = aRun();
        String oversized = "k".repeat(IdempotentIngestion.MAX_KEY_LENGTH + 1);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> idempotent.ingest(MARIE, run.id(), aBatch(1), Optional.of(oversized)));
    }
}
