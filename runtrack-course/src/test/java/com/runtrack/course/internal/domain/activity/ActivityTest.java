package com.runtrack.course.internal.domain.activity;

import com.runtrack.course.internal.domain.access.ActivityAudience;
import com.runtrack.course.internal.domain.track.DeviceClockSkew;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.id.UserId;
import com.runtrack.shared.error.ConflictException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ActivityTest {

    private static final ActivityId ID = new ActivityId(UUID.fromString("018f4c1e-0000-7000-8000-0000000000aa"));
    private static final UserId MARIE = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000001"));
    private static final Instant START = Instant.parse("2026-08-29T08:00:00Z");

    private static Activity liveActivity() {
        return Activity.start(ID, MARIE, ActivityType.RUN, "Sortie du matin", "Boucle de la citadelle",
                AudienceScope.FOLLOWERS, START, DeviceClockSkew.NONE);
    }

    @Test
    void startsLive() {
        Activity activity = liveActivity();

        assertThat(activity.status()).isInstanceOf(ActivityStatus.Live.class);
        assertThat(activity.status().since()).isEqualTo(START);
        assertThat(activity.title()).isEqualTo("Sortie du matin");
        assertThat(activity.description()).contains("Boucle de la citadelle");
        assertThat(activity.scope()).isEqualTo(AudienceScope.FOLLOWERS);
        assertThat(activity.type()).isEqualTo(ActivityType.RUN);
        assertThat(activity.ownerId()).isEqualTo(MARIE);
        assertThat(activity.id()).isEqualTo(ID);
        assertThat(activity.startedAt()).isEqualTo(START);
        assertThat(activity.clockSkew()).isEqualTo(DeviceClockSkew.NONE);
    }

    @Nested
    class LegalTransitions {

        @Test
        void livePausesThenResumes() {
            Activity activity = liveActivity();

            activity.pause(START.plusSeconds(600));
            assertThat(activity.status()).isInstanceOf(ActivityStatus.Paused.class);

            activity.resume(START.plusSeconds(700));
            assertThat(activity.status()).isInstanceOf(ActivityStatus.Live.class);
            assertThat(activity.status().since()).isEqualTo(START.plusSeconds(700));
        }

        @Test
        void liveFinishes() {
            Activity activity = liveActivity();

            activity.finish(START.plusSeconds(3_600));

            assertThat(activity.status()).isInstanceOf(ActivityStatus.Finished.class);
        }

        @Test
        void pausedFinishes() {
            Activity activity = liveActivity();
            activity.pause(START.plusSeconds(600));

            activity.finish(START.plusSeconds(900));

            assertThat(activity.status()).isInstanceOf(ActivityStatus.Finished.class);
        }

        @Test
        void liveIsDiscarded() {
            Activity activity = liveActivity();

            activity.discard(START.plusSeconds(60));

            assertThat(activity.status()).isInstanceOf(ActivityStatus.Discarded.class);
        }

        @Test
        void pausedIsDiscarded() {
            Activity activity = liveActivity();
            activity.pause(START.plusSeconds(60));

            activity.discard(START.plusSeconds(120));

            assertThat(activity.status()).isInstanceOf(ActivityStatus.Discarded.class);
        }
    }

    @Nested
    class IllegalTransitions {

        @Test
        void aLiveActivityCannotResume() {
            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> liveActivity().resume(START.plusSeconds(10)))
                    .extracting(ConflictException::code)
                    .isEqualTo("ACTIVITY_NOT_PAUSED");
        }

        @Test
        void aPausedActivityCannotPauseAgain() {
            Activity activity = liveActivity();
            activity.pause(START.plusSeconds(10));

            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> activity.pause(START.plusSeconds(20)))
                    .extracting(ConflictException::code)
                    .isEqualTo("ACTIVITY_NOT_LIVE");
        }

        @Test
        void aFinishedActivityIsFrozen() {
            Activity activity = liveActivity();
            activity.finish(START.plusSeconds(3_600));

            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> activity.pause(START.plusSeconds(3_700)))
                    .extracting(ConflictException::code)
                    .isEqualTo("ACTIVITY_NOT_LIVE");
            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> activity.resume(START.plusSeconds(3_700)));
            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> activity.finish(START.plusSeconds(3_700)))
                    .extracting(ConflictException::code)
                    .isEqualTo("ACTIVITY_ALREADY_ENDED");
            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> activity.discard(START.plusSeconds(3_700)))
                    .extracting(ConflictException::code)
                    .isEqualTo("ACTIVITY_ALREADY_ENDED");
        }

        @Test
        void aDiscardedActivityIsFrozen() {
            Activity activity = liveActivity();
            activity.discard(START.plusSeconds(60));

            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> activity.finish(START.plusSeconds(70)));
            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> activity.discard(START.plusSeconds(70)));
            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> activity.resume(START.plusSeconds(70)));
        }
    }

    @Nested
    class PointAcceptance {

        @Test
        void onlyWhileLive() {
            Activity activity = liveActivity();

            activity.requireAcceptingPoints();

            activity.pause(START.plusSeconds(60));
            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(activity::requireAcceptingPoints)
                    .extracting(ConflictException::code)
                    .isEqualTo("ACTIVITY_NOT_ACCEPTING_POINTS");
        }

        @Test
        void neverOnceFinished() {
            Activity activity = liveActivity();
            activity.finish(START.plusSeconds(600));

            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(activity::requireAcceptingPoints);
        }
    }

    @Nested
    class ElapsedTime {

        @Test
        void keepsRunningWhileLive() {
            Activity activity = liveActivity();

            assertThat(activity.elapsedAt(START.plusSeconds(1_800))).isEqualTo(Duration.ofMinutes(30));
        }

        /** Une fois terminée, le chronomètre ne bouge plus, quelle que soit l'heure de lecture. */
        @Test
        void freezesOnceTerminal() {
            Activity activity = liveActivity();
            activity.finish(START.plusSeconds(3_600));

            assertThat(activity.elapsedAt(START.plusSeconds(99_999))).isEqualTo(Duration.ofHours(1));
        }

        @Test
        void neverGoesNegative() {
            Activity activity = liveActivity();

            assertThat(activity.elapsedAt(START.minusSeconds(60))).isZero();
        }
    }

    @Nested
    class Editing {

        @Test
        void trimsTitleAndDescription() {
            Activity activity = liveActivity();

            activity.rename("  Fractionné  ", "  30/30  ");

            assertThat(activity.title()).isEqualTo("Fractionné");
            assertThat(activity.description()).contains("30/30");
        }

        @Test
        void acceptsAnAbsentDescription() {
            Activity activity = liveActivity();

            activity.rename("Sortie", null);

            assertThat(activity.description()).isEmpty();
        }

        @Test
        void refusesAnEmptyTitle() {
            Activity activity = liveActivity();

            assertThatIllegalArgumentException().isThrownBy(() -> activity.rename(null, null));
            assertThatIllegalArgumentException().isThrownBy(() -> activity.rename("   ", null));
        }

        @Test
        void refusesOversizedText() {
            Activity activity = liveActivity();

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> activity.rename("x".repeat(121), null))
                    .withMessageContaining("Titre trop long");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> activity.rename("Sortie", "x".repeat(2_001)))
                    .withMessageContaining("Description trop longue");
        }

        @Test
        void changesVisibility() {
            Activity activity = liveActivity();

            activity.changeScope(AudienceScope.PRIVATE);

            assertThat(activity.scope()).isEqualTo(AudienceScope.PRIVATE);
            assertThatIllegalArgumentException().isThrownBy(() -> activity.changeScope(null));
        }
    }

    @Test
    void refusesToStartWithoutItsEssentials() {
        assertThatIllegalArgumentException().isThrownBy(() -> Activity.start(
                null, MARIE, ActivityType.RUN, "t", null, AudienceScope.PUBLIC, START, DeviceClockSkew.NONE));
        assertThatIllegalArgumentException().isThrownBy(() -> Activity.start(
                ID, null, ActivityType.RUN, "t", null, AudienceScope.PUBLIC, START, DeviceClockSkew.NONE));
        assertThatIllegalArgumentException().isThrownBy(() -> Activity.start(
                ID, MARIE, null, "t", null, AudienceScope.PUBLIC, START, DeviceClockSkew.NONE));
        assertThatIllegalArgumentException().isThrownBy(() -> Activity.start(
                ID, MARIE, ActivityType.RUN, "t", null, null, START, DeviceClockSkew.NONE));
        assertThatIllegalArgumentException().isThrownBy(() -> Activity.start(
                ID, MARIE, ActivityType.RUN, "t", null, AudienceScope.PUBLIC, null, DeviceClockSkew.NONE));
        assertThatIllegalArgumentException().isThrownBy(() -> Activity.start(
                ID, MARIE, ActivityType.RUN, "t", null, AudienceScope.PUBLIC, START, null));
    }

    @Test
    void exposesItsAudienceComposedWithTheOwnerAccount() {
        Activity activity = liveActivity();

        ActivityAudience audience = activity.audienceWith(AudienceScope.PRIVATE);

        assertThat(audience.effectiveScope()).isEqualTo(AudienceScope.PRIVATE);
        assertThat(audience.ownerId()).isEqualTo(MARIE);
    }
}
