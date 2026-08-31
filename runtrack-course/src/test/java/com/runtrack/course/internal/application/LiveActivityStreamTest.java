package com.runtrack.course.internal.application;

import static com.runtrack.course.internal.domain.fixture.TrackPointBuilder.START;
import static com.runtrack.course.internal.domain.fixture.TrackPointBuilder.aPoint;
import static org.assertj.core.api.Assertions.assertThat;

import com.runtrack.course.internal.application.fixture.CourseDoubles;
import com.runtrack.course.internal.domain.activity.Activity;
import com.runtrack.course.internal.domain.activity.ActivityType;
import com.runtrack.course.internal.domain.live.LiveEvent;
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

/** L'instantané : ce qu'un spectateur voit à la seconde où il se branche. */
class LiveActivityStreamTest {

    private static final UserId MARIE = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000001"));
    private static final Clock AT_START = Clock.fixed(START, ZoneOffset.UTC);

    private CourseDoubles.Points points;
    private ActivityLifecycle lifecycle;
    private LiveActivityStream stream;

    @BeforeEach
    void setUp() {
        var activities = new CourseDoubles.Activities();
        var stats = new CourseDoubles.Stats();
        var relations = new CourseDoubles.Relations();
        var users = new CourseDoubles.Users();
        points = new CourseDoubles.Points();
        ApplicationEventPublisher publisher = event -> { };

        lifecycle = new ActivityLifecycle(activities, stats, relations, publisher,
                new CourseDoubles.LivePublisher(), AT_START, new Random(7));
        stream = new LiveActivityStream(
                new ActivityQueries(activities, stats, relations, users, AT_START), points);
    }

    private Activity aRun() {
        return lifecycle.start(MARIE, ActivityType.RUN, "Sortie", null, AudienceScope.PUBLIC, null);
    }

    private void store(Activity run, int count) {
        points.appendAll(run.id(), IntStream.rangeClosed(1, count)
                .mapToObj(index -> aPoint().sequence(index).secondsAfterStart(index).build())
                .toList());
    }

    @Test
    void opensWithTheStateThenTheStatsThenTheTrack() {
        Activity run = aRun();
        store(run, 3);

        List<LiveEvent> snapshot = stream.snapshotOf(run);

        assertThat(snapshot).hasSize(5);
        assertThat(snapshot.get(0)).isInstanceOf(LiveEvent.Status.class);
        assertThat(snapshot.get(1)).isInstanceOf(LiveEvent.Stats.class);
        assertThat(snapshot.subList(2, 5)).allMatch(LiveEvent.Position.class::isInstance);
    }

    /** Sans points, l'instantané reste utile : le spectateur sait au moins que la course a démarré. */
    @Test
    void aRunWithoutAnyPointStillAnnouncesItself() {
        List<LiveEvent> snapshot = stream.snapshotOf(aRun());

        assertThat(snapshot).hasSize(2);
        assertThat(((LiveEvent.Status) snapshot.getFirst()).status()).isEqualTo("Live");
    }

    /** Une sortie longue ne fait pas voyager dix mille points à chaque connexion. */
    @Test
    void aLongRunOnlySendsItsMostRecentPoints() {
        Activity run = aRun();
        store(run, LiveActivityStream.SNAPSHOT_POINTS + 50);

        List<LiveEvent> positions = stream.snapshotOf(run).stream()
                .filter(LiveEvent.Position.class::isInstance)
                .toList();

        assertThat(positions).hasSize(LiveActivityStream.SNAPSHOT_POINTS);
        // Les derniers, et dans l'ordre du tracé : c'est la fin du parcours qu'on redessine.
        assertThat(((LiveEvent.Position) positions.getFirst()).sequenceNumber()).isEqualTo(51);
        assertThat(((LiveEvent.Position) positions.getLast()).sequenceNumber())
                .isEqualTo(LiveActivityStream.SNAPSHOT_POINTS + 50);
    }

    @Test
    void aPausedRunAnnouncesThatItIsPaused() {
        Activity run = aRun();
        lifecycle.pause(MARIE, run.id());

        assertThat(((LiveEvent.Status) stream.snapshotOf(run).getFirst()).status()).isEqualTo("Paused");
    }
}
