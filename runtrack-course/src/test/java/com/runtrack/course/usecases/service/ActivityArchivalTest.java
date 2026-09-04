package com.runtrack.course.usecases.service;

import static com.runtrack.course.usecases.model.fixture.TrackPointBuilder.START;
import static com.runtrack.course.usecases.model.fixture.TrackPointBuilder.aPoint;
import static org.assertj.core.api.Assertions.assertThat;

import com.runtrack.course.usecases.fixture.CourseDoubles;
import com.runtrack.course.usecases.port.ActivityArchive;
import com.runtrack.course.usecases.model.activity.Activity;
import com.runtrack.course.usecases.model.activity.ActivityType;
import com.runtrack.course.usecases.model.track.TrackPoint;
import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.id.UserId;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/** L'historisation de fin de course : ce qui est figé, et ce qui ne l'est délibérément pas. */
class ActivityArchivalTest {

    private static final UserId MARIE = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000001"));
    private static final Clock AT_START = Clock.fixed(START, ZoneOffset.UTC);

    private CourseDoubles.Points points;
    private CourseDoubles.Archive archive;
    private ActivityLifecycle lifecycle;
    private ActivityArchival archival;

    @BeforeEach
    void setUp() {
        var activities = new CourseDoubles.Activities();
        var stats = new CourseDoubles.Stats();
        var relations = new CourseDoubles.Relations();
        points = new CourseDoubles.Points();
        archive = new CourseDoubles.Archive();
        ApplicationEventPublisher publisher = event -> { };

        archival = new ActivityArchival(points, archive, AT_START);
        lifecycle = new ActivityLifecycle(activities, stats, relations, publisher, archival,
                new CourseDoubles.LivePublisher(), AT_START, new Random(7));
    }

    private Activity aRun() {
        return lifecycle.start(MARIE, ActivityType.RUN, "Sortie", null, AudienceScope.PUBLIC, null);
    }

    /** Trois mètres par seconde vers l'est : une allure de coureur, sur une ligne. */
    private void record(Activity run, int count) {
        List<TrackPoint> track = IntStream.rangeClosed(1, count)
                .mapToObj(index -> aPoint().sequence(index)
                        .metersEast(3d * index).secondsAfterStart(index).build())
                .toList();
        points.appendAll(run.id(), track);
    }

    @Test
    void finishingFreezesTheTrackAndItsSplits() {
        Activity run = aRun();
        record(run, 1_200);

        lifecycle.finish(MARIE, run.id());

        ActivityArchive.ArchivedTrack track = archive.find(run.id()).orElseThrow();
        assertThat(track.rawPointCount()).isEqualTo(1_200);
        assertThat(track.polyline()).isNotBlank();
        // 3 600 mètres : trois kilomètres pleins et un tronçon entamé.
        assertThat(archival.splitsOf(run.id())).hasSize(4);
    }

    /** La simplification est la raison d'être de l'archive : la trace doit maigrir. */
    @Test
    void theStoredTrackIsFarShorterThanTheRawOne() {
        Activity run = aRun();
        record(run, 1_200);

        lifecycle.finish(MARIE, run.id());

        ActivityArchive.ArchivedTrack track = archive.find(run.id()).orElseThrow();
        assertThat(track.pointCount()).isLessThan(track.rawPointCount() / 10);
        assertThat(track.positions()).hasSize(track.pointCount());
    }

    @Test
    void aRunStillGoingGetsAThumbnailFromItsPointsSoFar() {
        Activity run = aRun();
        record(run, 300);

        // Pas de gel : elle court encore. Sans échantillon, sa carte serait un cadre
        // vide dans le fil jusqu'à ce qu'elle se termine.
        assertThat(archival.previewsOf(List.of(run.id()))).containsKey(run.id());
    }

    @Test
    void theFreezeAlsoKeepsAThumbnailOfTheTrack() {
        Activity run = aRun();
        // Un parcours qui tourne : sur une ligne droite, les deux simplifications rendent le
        // même segment, et la vignette n'aurait rien prouvé.
        List<TrackPoint> winding = IntStream.rangeClosed(1, 600)
                .mapToObj(index -> aPoint().sequence(index)
                        // Un lacet : la latitude alterne, sans quoi tous les points sont
                        // alignés et les deux simplifications rendent le même segment.
                        .at(new com.runtrack.shared.measure.GeoPoint(
                                50.63 + (index % 2 == 0 ? 0.0006 : 0),
                                3.06 + index * 0.0004))
                        .secondsAfterStart(index * 2L).build())
                .toList();
        points.appendAll(run.id(), winding);

        lifecycle.finish(MARIE, run.id());

        ActivityArchive.ArchivedTrack track = archive.find(run.id()).orElseThrow();
        // La vignette pèse moins que la trace d'affichage : c'est elle que dessinent les
        // cartes du fil, vingt par page.
        assertThat(track.previewPolyline()).isNotBlank();
        assertThat(track.previewPolyline().length()).isLessThan(track.polyline().length());
        assertThat(archive.previewsOf(List.of(run.id()))).containsKey(run.id());
    }

    /**
     * Une course arrêtée sans le moindre point n'écrit rien.
     *
     * <p>Une trace vide obligerait chaque lecteur à distinguer « pas encore historisée » de
     * « historisée, mais rien dedans » — deux états qui s'affichent pareil.
     */
    @Test
    void aRunWithoutAnyPointArchivesNothing() {
        Activity run = aRun();

        lifecycle.finish(MARIE, run.id());

        assertThat(archive.find(run.id())).isEmpty();
        assertThat(archival.splitsOf(run.id())).isEmpty();
    }

    /** Une course d'un seul point a une position, pas une ligne : elle s'archive quand même. */
    @Test
    void aSinglePointStillArchives() {
        Activity run = aRun();
        record(run, 1);

        lifecycle.finish(MARIE, run.id());

        assertThat(archive.find(run.id())).isPresent();
        assertThat(archival.splitsOf(run.id())).isEmpty();
    }

    /** Historiser deux fois ne duplique rien : l'écriture est une mise à jour sur la course. */
    @Test
    void freezingTwiceLeavesOneTrack() {
        Activity run = aRun();
        record(run, 100);

        archival.freeze(run);
        archival.freeze(run);

        assertThat(archive.find(run.id())).isPresent();
    }

    /** Supprimer une course emporte ses points et sa trace : rien ne doit lui survivre. */
    @Test
    void deletingARunTakesItsTrackAndItsPointsAlong() {
        Activity run = aRun();
        record(run, 100);
        lifecycle.finish(MARIE, run.id());

        lifecycle.delete(MARIE, run.id());

        assertThat(archive.find(run.id())).isEmpty();
        assertThat(points.count(run.id())).isZero();
    }
}
