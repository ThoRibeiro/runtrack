package com.runtrack.course.internal.application;

import static com.runtrack.course.internal.domain.fixture.TrackPointBuilder.START;
import static com.runtrack.course.internal.domain.fixture.TrackPointBuilder.aPoint;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.runtrack.course.internal.application.fixture.CourseDoubles;
import com.runtrack.course.internal.domain.activity.Activity;
import com.runtrack.course.internal.domain.activity.ActivityType;
import com.runtrack.course.internal.domain.live.LiveEvent;
import com.runtrack.course.internal.domain.stats.StatsAccumulator;
import com.runtrack.course.internal.domain.track.PointRejection;
import com.runtrack.course.internal.domain.track.TrackPoint;
import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.error.ConflictException;
import com.runtrack.shared.error.NotFoundException;
import com.runtrack.shared.id.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/**
 * L'ingestion d'un lot, et surtout la propriété qui justifie tout son ordre d'opérations :
 * rejouer un lot ne fausse aucune statistique.
 */
class PointIngestionTest {

    private static final UserId MARIE = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000001"));
    private static final UserId PAUL = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000002"));

    /** La course démarre à l'instant du premier point ; le serveur reçoit le lot une heure après. */
    private static final Clock AT_START = Clock.fixed(START, ZoneOffset.UTC);
    private static final Clock AN_HOUR_LATER = Clock.fixed(START.plusSeconds(3_600), ZoneOffset.UTC);

    private CourseDoubles.Activities activities;
    private CourseDoubles.Stats stats;
    private CourseDoubles.Points points;
    private CourseDoubles.LivePublisher live;
    private ActivityLifecycle lifecycle;
    private PointIngestion ingestion;

    @BeforeEach
    void setUp() {
        activities = new CourseDoubles.Activities();
        stats = new CourseDoubles.Stats();
        points = new CourseDoubles.Points();
        var relations = new CourseDoubles.Relations();
        var users = new CourseDoubles.Users();
        ApplicationEventPublisher publisher = event -> { };

        live = new CourseDoubles.LivePublisher();
        lifecycle = new ActivityLifecycle(activities, stats, relations, publisher,
                new ActivityArchival(points, new CourseDoubles.Archive(), AT_START),
                live, AT_START, new Random(7));
        var queries = new ActivityQueries(activities, stats, relations, users, AN_HOUR_LATER);
        ingestion = new PointIngestion(activities, stats, points, queries, live, AN_HOUR_LATER);
    }

    private Activity aRun() {
        return lifecycle.start(MARIE, ActivityType.RUN, "Sortie", null, AudienceScope.PUBLIC, null);
    }

    /** Cent mètres toutes les trente secondes : une allure de coureur, jamais filtrée. */
    private static List<TrackPoint> aStraightLine(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(index -> aPoint()
                        .sequence(index)
                        .metersEast(100d * index)
                        .secondsAfterStart(30L * index)
                        .build())
                .toList();
    }

    @Test
    void acceptsABatchAndAdvancesTheCursor() {
        Activity run = aRun();

        IngestionResult result = ingestion.ingest(MARIE, run.id(), aStraightLine(4));

        assertThat(result.acceptedCount()).isEqualTo(4);
        assertThat(result.lastAcceptedSequence()).isEqualTo(4);
        assertThat(result.rejected()).isEmpty();
        assertThat(result.stats().distance().meters()).isGreaterThan(0);
        assertThat(points.count(run.id())).isEqualTo(4);
    }

    @Test
    void replayingTheSameBatchChangesNoStatistic() {
        Activity run = aRun();
        List<TrackPoint> batch = aStraightLine(4);

        IngestionResult first = ingestion.ingest(MARIE, run.id(), batch);
        IngestionResult replay = ingestion.ingest(MARIE, run.id(), batch);

        assertThat(replay.acceptedCount()).isZero();
        assertThat(replay.stats()).isEqualTo(first.stats());
        assertThat(replay.lastAcceptedSequence()).isEqualTo(first.lastAcceptedSequence());
        assertThat(replay.rejected())
                .extracting(IngestionResult.Rejected::reason)
                .containsOnly(PointRejection.DUPLICATE_SEQUENCE);
        assertThat(points.count(run.id())).isEqualTo(4);
    }

    /**
     * Le cas réel du rejeu : le tampon du client recouvre partiellement ce que le serveur a
     * déjà reçu. Seule la partie neuve doit compter.
     */
    @Test
    void anOverlappingBatchOnlyAppliesItsNewPoints() {
        Activity run = aRun();
        List<TrackPoint> all = aStraightLine(6);

        ingestion.ingest(MARIE, run.id(), all.subList(0, 4));
        IngestionResult overlap = ingestion.ingest(MARIE, run.id(), all);

        assertThat(overlap.acceptedCount()).isEqualTo(2);
        assertThat(overlap.lastAcceptedSequence()).isEqualTo(6);
        assertThat(points.count(run.id())).isEqualTo(6);
    }

    @Test
    void aBatchArrivingOutOfOrderIsSortedBeforeAccumulation() {
        Activity run = aRun();
        List<TrackPoint> ordered = aStraightLine(5);
        var shuffled = new java.util.ArrayList<>(ordered);
        java.util.Collections.shuffle(shuffled, new Random(11));

        IngestionResult result = ingestion.ingest(MARIE, run.id(), shuffled);

        assertThat(result.acceptedCount()).isEqualTo(5);
        assertThat(result.stats()).isEqualTo(replayedFromScratch(ordered));
    }

    /**
     * La propriété du §4 : l'état incrémental est déterministe. Si elle tombe, toute la
     * mécanique de curseur et de tri ne sert plus à rien.
     */
    @Test
    void ingestingInThreeBatchesGivesTheSameStatsAsOne() {
        Activity run = aRun();
        List<TrackPoint> all = aStraightLine(9);

        ingestion.ingest(MARIE, run.id(), all.subList(0, 3));
        ingestion.ingest(MARIE, run.id(), all.subList(3, 6));
        IngestionResult piecewise = ingestion.ingest(MARIE, run.id(), all.subList(6, 9));

        assertThat(piecewise.stats()).isEqualTo(replayedFromScratch(all));
    }

    @Test
    void aPointTooImpreciseIsRejectedWithItsReason() {
        Activity run = aRun();
        TrackPoint blurred = aPoint().sequence(1).secondsAfterStart(30).accuracy(120).build();

        IngestionResult result = ingestion.ingest(MARIE, run.id(), List.of(blurred));

        assertThat(result.acceptedCount()).isZero();
        assertThat(result.rejected()).singleElement()
                .isEqualTo(new IngestionResult.Rejected(1, PointRejection.ACCURACY_TOO_LOW));
        assertThat(points.count(run.id())).isZero();
    }

    /** Un lot entièrement rejeté ne doit rien écrire : ni point, ni accumulateur inchangé. */
    @Test
    void aFullyRejectedBatchWritesNothing() {
        Activity run = aRun();
        ingestion.ingest(MARIE, run.id(), aStraightLine(3));
        StatsAccumulator before = stats.find(run.id()).orElseThrow();

        ingestion.ingest(MARIE, run.id(), aStraightLine(3));

        assertThat(stats.find(run.id())).contains(before);
    }

    @Test
    void anotherRunnerCannotFeedSomeoneElsesRun() {
        Activity run = aRun();

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> ingestion.ingest(PAUL, run.id(), aStraightLine(2)))
                .withMessageContaining("introuvable");
    }

    @Test
    void aPausedRunRefusesPoints() {
        Activity run = aRun();
        lifecycle.pause(MARIE, run.id());

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> ingestion.ingest(MARIE, run.id(), aStraightLine(2)));
    }

    @Test
    void anUnknownRunIsNotFound() {
        assertThatExceptionOfType(NotFoundException.class).isThrownBy(() -> ingestion.ingest(
                MARIE, com.runtrack.shared.id.ActivityId.generate(AT_START, new Random(3)), aStraightLine(1)));
    }

    /**
     * L'horodatage du téléphone est ramené à l'heure serveur une seule fois, avec la dérive
     * mesurée au démarrage : deux lots envoyés à des moments différents partagent la même
     * référence de temps.
     */
    @Test
    void theDeviceClockDriftIsCorrectedOnIngestedPoints() {
        Instant deviceTime = START.plusSeconds(120);
        Activity drifting = lifecycle.start(
                MARIE, ActivityType.RUN, "Montre en avance", null, AudienceScope.PUBLIC, deviceTime);

        ingestion.ingest(MARIE, drifting.id(), List.of(
                aPoint().sequence(1).recordedAt(deviceTime.plusSeconds(30)).build()));

        assertThat(points.findLast(drifting.id())).get()
                .extracting(TrackPoint::recordedAt)
                .isEqualTo(START.plusSeconds(30));
    }

    /**
     * Ce qui part vers les spectateurs : chaque position, et les statistiques une seule fois.
     *
     * <p>Une version des statistiques par point ferait clignoter l'écran du spectateur sur des
     * états intermédiaires que la course n'a jamais eus.
     */
    @Test
    void broadcastsEveryAcceptedPointAndTheStatisticsOnce() {
        Activity run = aRun();

        ingestion.ingest(MARIE, run.id(), aStraightLine(4));

        assertThat(live.broadcast()).hasSize(5);
        assertThat(live.broadcast().subList(0, 4)).allMatch(LiveEvent.Position.class::isInstance);
        assertThat(live.broadcast().getLast()).isInstanceOf(LiveEvent.Stats.class);
    }

    @Test
    void aFullyRejectedBatchBroadcastsNothing() {
        Activity run = aRun();
        ingestion.ingest(MARIE, run.id(), aStraightLine(3));
        int alreadySent = live.broadcast().size();

        ingestion.ingest(MARIE, run.id(), aStraightLine(3));

        assertThat(live.broadcast()).hasSize(alreadySent);
    }

    /** Rejoue depuis zéro tous les points stockés : la référence dont tout le reste est comparé. */
    private com.runtrack.course.internal.domain.stats.ActivityStats replayedFromScratch(
            List<TrackPoint> allPoints) {

        Activity fresh = aRun();
        var scratchStats = new CourseDoubles.Stats();
        var scratchPoints = new CourseDoubles.Points();
        var relations = new CourseDoubles.Relations();
        var users = new CourseDoubles.Users();
        var queries = new ActivityQueries(activities, scratchStats, relations, users, AN_HOUR_LATER);

        return new PointIngestion(activities, scratchStats, scratchPoints, queries,
                new CourseDoubles.LivePublisher(), AN_HOUR_LATER)
                .ingest(MARIE, fresh.id(), allPoints)
                .stats();
    }
}
