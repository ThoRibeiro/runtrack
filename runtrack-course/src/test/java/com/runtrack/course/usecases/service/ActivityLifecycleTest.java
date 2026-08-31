package com.runtrack.course.usecases.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.runtrack.course.event.ActivityDiscarded;
import com.runtrack.course.event.ActivityFinished;
import com.runtrack.course.event.ActivityPaused;
import com.runtrack.course.event.ActivityResumed;
import com.runtrack.course.event.ActivityStarted;
import com.runtrack.course.usecases.fixture.CourseDoubles;
import com.runtrack.course.usecases.model.activity.Activity;
import com.runtrack.course.usecases.model.activity.ActivityType;
import com.runtrack.course.usecases.model.live.LiveEvent;
import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.error.ConflictException;
import com.runtrack.shared.error.NotFoundException;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class ActivityLifecycleTest {

    private static final UserId MARIE = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000001"));
    private static final UserId PAUL = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000002"));
    private static final Instant NOW = Instant.parse("2026-08-29T08:00:00Z");

    private CourseDoubles.Activities activities;
    private CourseDoubles.Stats stats;
    private CourseDoubles.Relations relations;
    private CourseDoubles.LivePublisher live;
    private CourseDoubles.Points points;
    private ActivityArchival archival;
    private List<Object> published;
    private ActivityLifecycle lifecycle;

    @BeforeEach
    void setUp() {
        activities = new CourseDoubles.Activities();
        stats = new CourseDoubles.Stats();
        relations = new CourseDoubles.Relations();
        published = new ArrayList<>();
        ApplicationEventPublisher publisher = published::add;
        live = new CourseDoubles.LivePublisher();
        points = new CourseDoubles.Points();
        archival = new ActivityArchival(points, new CourseDoubles.Archive(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        lifecycle = new ActivityLifecycle(activities, stats, relations, publisher, archival, live,
                Clock.fixed(NOW, ZoneOffset.UTC), new java.util.Random(7));
    }

    private Activity startRun() {
        return lifecycle.start(MARIE, ActivityType.RUN, "Sortie", null, AudienceScope.FOLLOWERS, null);
    }

    @Nested
    class Starting {

        @Test
        void createsTheActivityItsAccumulatorAndAnnouncesIt() {
            Activity activity = startRun();

            assertThat(activities.findById(activity.id())).isPresent();
            assertThat(stats.holds(activity.id())).isTrue();
            assertThat(published).singleElement()
                    .isInstanceOfSatisfying(ActivityStarted.class, event -> {
                        assertThat(event.ownerId()).isEqualTo(MARIE);
                        assertThat(event.activityType()).isEqualTo("RUN");
                        assertThat(event.at()).isEqualTo(NOW);
                    });
        }

        /** L'événement porte la portée déjà composée : le fan-out n'a pas à la recalculer. */
        @Test
        void theEventCarriesTheEffectiveScope() {
            relations.withAccountScope(AudienceScope.PRIVATE);

            startRun();

            assertThat(published).singleElement()
                    .isInstanceOfSatisfying(ActivityStarted.class,
                            event -> assertThat(event.effectiveScope()).isEqualTo("PRIVATE"));
        }

        @Test
        void measuresTheDeviceClockDriftWhenTheClientSendsIt() {
            Activity activity = lifecycle.start(MARIE, ActivityType.RUN, "Sortie", null,
                    AudienceScope.PUBLIC, NOW.minusSeconds(90));

            assertThat(activity.clockSkew().offset()).isEqualTo(java.time.Duration.ofSeconds(90));
        }

        @Test
        void refusesToStartWithAWildlyWrongDeviceClock() {
            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> lifecycle.start(MARIE, ActivityType.RUN, "Sortie", null,
                            AudienceScope.PUBLIC, NOW.minusSeconds(3_600)))
                    .extracting(ConflictException::code)
                    .isEqualTo("DEVICE_CLOCK_TOO_FAR_OFF");
        }
    }

    @Nested
    class Transitions {

        @Test
        void pauseResumeFinishEachAnnounceThemselves() {
            Activity activity = startRun();
            published.clear();

            lifecycle.pause(MARIE, activity.id());
            lifecycle.resume(MARIE, activity.id());
            lifecycle.finish(MARIE, activity.id());

            assertThat(published).hasSize(3);
            assertThat(published.get(0)).isInstanceOf(ActivityPaused.class);
            assertThat(published.get(1)).isInstanceOf(ActivityResumed.class);
            assertThat(published.get(2)).isInstanceOf(ActivityFinished.class);
        }

        @Test
        void discardingAnnouncesItself() {
            Activity activity = startRun();
            published.clear();

            lifecycle.discard(MARIE, activity.id());

            assertThat(published).singleElement().isInstanceOf(ActivityDiscarded.class);
        }

        @Test
        void anIllegalTransitionIsRefused() {
            Activity activity = startRun();

            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> lifecycle.resume(MARIE, activity.id()))
                    .extracting(ConflictException::code)
                    .isEqualTo("ACTIVITY_NOT_PAUSED");
        }
    }

    @Nested
    class Ownership {

        /**
         * Agir sur la course d'autrui rend « introuvable », jamais « interdit » : un 403
         * confirmerait l'existence de la course à qui n'a pas le droit de la voir.
         */
        @Test
        void actingOnSomeoneElsesActivityLooksLikeItDoesNotExist() {
            Activity activity = startRun();

            assertThatExceptionOfType(NotFoundException.class)
                    .isThrownBy(() -> lifecycle.pause(PAUL, activity.id()))
                    .extracting(NotFoundException::code)
                    .isEqualTo("ACTIVITY_NOT_FOUND");
            assertThatExceptionOfType(NotFoundException.class)
                    .isThrownBy(() -> lifecycle.finish(PAUL, activity.id()));
            assertThatExceptionOfType(NotFoundException.class)
                    .isThrownBy(() -> lifecycle.delete(PAUL, activity.id()));
        }

        @Test
        void anUnknownActivityIsNotFound() {
            ActivityId ghost = new ActivityId(UUID.fromString("018f4c1e-0000-7000-8000-0000000000ff"));

            assertThatExceptionOfType(NotFoundException.class)
                    .isThrownBy(() -> lifecycle.pause(MARIE, ghost));
        }
    }

    @Nested
    class Editing {

        @Test
        void renamesAndChangesVisibility() {
            Activity activity = startRun();

            lifecycle.rename(MARIE, activity.id(), "Fractionné", "30/30");
            lifecycle.changeScope(MARIE, activity.id(), AudienceScope.PRIVATE);

            Activity reloaded = activities.findById(activity.id()).orElseThrow();
            assertThat(reloaded.title()).isEqualTo("Fractionné");
            assertThat(reloaded.description()).contains("30/30");
            assertThat(reloaded.scope()).isEqualTo(AudienceScope.PRIVATE);
        }

        /** Supprimer une course emporte son accumulateur : rien ne doit rester orphelin. */
        @Test
        void deletingRemovesTheAccumulatorToo() {
            Activity activity = startRun();

            lifecycle.delete(MARIE, activity.id());

            assertThat(activities.size()).isZero();
            assertThat(stats.holds(activity.id())).isFalse();
        }
    }

    @Nested
    class Broadcasting {

        @Test
        void aPauseAndAResumeAreAnnouncedToSpectators() {
            Activity activity = startRun();

            lifecycle.pause(MARIE, activity.id());
            lifecycle.resume(MARIE, activity.id());

            assertThat(live.broadcast())
                    .extracting(event -> ((LiveEvent.Status) event).status())
                    .containsExactly("Paused", "Live");
        }

        /**
         * L'ordre compte : fermer le direct avant d'annoncer la fin priverait les spectateurs
         * connectés de la seule information qui les intéresse encore.
         */
        @Test
        void finishingAnnouncesTheEndBeforeClosingTheStream() {
            Activity activity = startRun();

            lifecycle.finish(MARIE, activity.id());

            assertThat(live.broadcast())
                    .singleElement()
                    .extracting(event -> ((LiveEvent.Status) event).status())
                    .isEqualTo("Finished");
            assertThat(live.closed()).containsExactly(activity.id());
        }

        @Test
        void discardingClosesTheStreamToo() {
            Activity activity = startRun();

            lifecycle.discard(MARIE, activity.id());

            assertThat(live.closed()).containsExactly(activity.id());
        }
    }
}
