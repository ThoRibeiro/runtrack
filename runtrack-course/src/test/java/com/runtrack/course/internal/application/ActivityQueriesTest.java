package com.runtrack.course.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.runtrack.course.internal.application.fixture.CourseDoubles;
import com.runtrack.course.internal.domain.access.AccessDecision;
import com.runtrack.course.internal.domain.access.ViewerRelation;
import com.runtrack.course.internal.domain.activity.Activity;
import com.runtrack.course.internal.domain.activity.ActivityType;
import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.access.Viewer;
import com.runtrack.shared.error.NotFoundException;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class ActivityQueriesTest {

    private static final UserId MARIE = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000001"));
    private static final UserId PAUL = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000002"));
    private static final Instant NOW = Instant.parse("2026-08-29T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final Viewer OWNER = new Viewer.AuthenticatedUser(MARIE);
    private static final Viewer STRANGER = new Viewer.AuthenticatedUser(PAUL);
    private static final Viewer ANONYMOUS = Viewer.Anonymous.INSTANCE;

    private CourseDoubles.Activities activities;
    private CourseDoubles.Stats stats;
    private CourseDoubles.Relations relations;
    private CourseDoubles.Users users;
    private ActivityLifecycle lifecycle;
    private ActivityQueries queries;

    @BeforeEach
    void setUp() {
        activities = new CourseDoubles.Activities();
        stats = new CourseDoubles.Stats();
        relations = new CourseDoubles.Relations();
        users = new CourseDoubles.Users();
        ApplicationEventPublisher publisher = event -> { };
        lifecycle = new ActivityLifecycle(activities, stats, relations, publisher,
                new ActivityArchival(new CourseDoubles.Points(), new CourseDoubles.Archive(), CLOCK),
                new CourseDoubles.LivePublisher(), CLOCK, new java.util.Random(7));
        queries = new ActivityQueries(activities, stats, relations, users, CLOCK);
    }

    private Activity runWith(AudienceScope scope) {
        return lifecycle.start(MARIE, ActivityType.RUN, "Sortie", null, scope, null);
    }

    @Test
    void theOwnerAlwaysSeesTheirRun() {
        Activity activity = runWith(AudienceScope.PRIVATE);

        assertThat(queries.decide(OWNER, activity)).isEqualTo(AccessDecision.GRANTED);
        assertThat(queries.canView(OWNER, activity.id())).isTrue();
    }

    @Test
    void aPublicRunIsVisibleToAnyone() {
        Activity activity = runWith(AudienceScope.PUBLIC);

        assertThat(queries.decide(STRANGER, activity)).isEqualTo(AccessDecision.GRANTED);
        assertThat(queries.decide(ANONYMOUS, activity)).isEqualTo(AccessDecision.GRANTED);
    }

    @Test
    void aFollowersOnlyRunNeedsAnAcceptedFollow() {
        Activity activity = runWith(AudienceScope.FOLLOWERS);

        assertThat(queries.decide(STRANGER, activity)).isEqualTo(AccessDecision.DENIED_NOT_A_FOLLOWER);

        relations.withRelation(ViewerRelation.acceptedFollower());
        assertThat(queries.decide(STRANGER, activity)).isEqualTo(AccessDecision.GRANTED);
    }

    @Test
    void aBlockCloseTheDoorEvenOnAPublicRun() {
        Activity activity = runWith(AudienceScope.PUBLIC);
        relations.withRelation(ViewerRelation.blocked());

        assertThat(queries.decide(STRANGER, activity)).isEqualTo(AccessDecision.DENIED_BLOCKED);
    }

    /** La composition des deux portées se vérifie aussi depuis la couche applicative. */
    @Test
    void aPublicRunOnAPrivateAccountIsPrivate() {
        relations.withAccountScope(AudienceScope.PRIVATE);
        Activity activity = runWith(AudienceScope.PUBLIC);
        relations.withRelation(ViewerRelation.acceptedFollower());

        assertThat(queries.decide(STRANGER, activity)).isEqualTo(AccessDecision.DENIED_PRIVATE);
    }

    /** Une course qu'on n'a pas le droit de voir se comporte comme si elle n'existait pas. */
    @Test
    void anInvisibleRunIsReportedAsNotFound() {
        Activity activity = runWith(AudienceScope.PRIVATE);

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> queries.require(STRANGER, activity.id()))
                .extracting(NotFoundException::code)
                .isEqualTo("ACTIVITY_NOT_FOUND");
        assertThat(queries.canView(STRANGER, activity.id())).isFalse();
    }

    @Test
    void anUnknownRunIsNotFoundAndNotViewable() {
        ActivityId ghost = new ActivityId(UUID.fromString("018f4c1e-0000-7000-8000-0000000000ff"));

        assertThatExceptionOfType(NotFoundException.class).isThrownBy(() -> queries.require(OWNER, ghost));
        assertThat(queries.canView(OWNER, ghost)).isFalse();
    }

    @Test
    void listingAProfileFiltersOutWhatTheReaderCannotSee() {
        runWith(AudienceScope.PUBLIC);
        runWith(AudienceScope.PRIVATE);

        assertThat(queries.ofOwner(OWNER, MARIE, java.util.Optional.empty(), 10)).hasSize(2);
        assertThat(queries.ofOwner(STRANGER, MARIE, java.util.Optional.empty(), 10)).hasSize(1);
    }

    @Test
    void theLiveListOnlyHoldsRunningActivities() {
        Activity live = runWith(AudienceScope.PUBLIC);
        Activity finished = runWith(AudienceScope.PUBLIC);
        lifecycle.finish(MARIE, finished.id());

        List<Activity> found = queries.liveOf(STRANGER, List.of(MARIE));

        assertThat(found).extracting(Activity::id).containsExactly(live.id());
    }

    @Test
    void statsAreEmptyBeforeAnyPointArrives() {
        Activity activity = runWith(AudienceScope.PUBLIC);

        var computed = queries.statsOf(activity);

        assertThat(computed.distance().meters()).isZero();
        assertThat(computed.movingTime()).isZero();
        assertThat(computed.averagePace()).isEmpty();
        assertThat(computed.estimatedCalories()).isEmpty();
    }

    /** L'estimation de calories n'apparaît que si le profil porte une masse. */
    @Test
    void statsStayCalorieFreeWithoutARunnerMass() {
        users.withMass(62);
        Activity activity = runWith(AudienceScope.PUBLIC);

        assertThat(queries.statsOf(activity).estimatedCalories()).isEmpty();
    }
}
