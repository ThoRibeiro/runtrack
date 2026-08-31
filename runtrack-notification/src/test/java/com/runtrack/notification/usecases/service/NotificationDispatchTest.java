package com.runtrack.notification.usecases.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.runtrack.notification.usecases.fixture.NotificationDoubles;
import com.runtrack.notification.usecases.model.inbox.Notification;
import com.runtrack.notification.usecases.model.inbox.NotificationPreferences;
import com.runtrack.notification.usecases.model.inbox.NotificationType;
import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Le fan-out du §7 : qui reçoit quoi, et ce qu'un rejeu ne refait pas. */
class NotificationDispatchTest {

    private static final UserId MARIE = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000001"));
    private static final UserId PAUL = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000002"));
    private static final UserId LEA = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000003"));
    private static final ActivityId RUN = new ActivityId(UUID.fromString("018f4c1e-0000-7000-8000-0000000000ff"));
    private static final Instant AT = Instant.parse("2026-08-29T08:00:00Z");

    private NotificationDoubles.Notifications notifications;
    private NotificationDoubles.Preferences preferences;
    private NotificationDoubles.Broadcaster broadcaster;
    private NotificationDoubles.Social social;
    private NotificationDispatch dispatch;

    @BeforeEach
    void setUp() {
        notifications = new NotificationDoubles.Notifications();
        preferences = new NotificationDoubles.Preferences();
        broadcaster = new NotificationDoubles.Broadcaster();
        social = new NotificationDoubles.Social().withFollowers(PAUL, LEA);
        dispatch = new NotificationDispatch(notifications, preferences, broadcaster, social);
    }

    @Test
    void everyAcceptedFollowerIsToldTheRunStarted() {
        dispatch.runStarted(RUN, MARIE, AudienceScope.PUBLIC, AT);

        assertThat(notifications.size()).isEqualTo(2);
        assertThat(broadcaster.delivered())
                .extracting(Notification::recipientId)
                .containsExactlyInAnyOrder(PAUL, LEA);
    }

    /** Le lien profond du §7 : on atterrit sur le suivi live, pas sur la fiche de la course. */
    @Test
    void aStartedRunLeadsStraightToTheLiveTracking() {
        dispatch.runStarted(RUN, MARIE, AudienceScope.PUBLIC, AT);

        assertThat(broadcaster.delivered().getFirst().deepLink())
                .isEqualTo("/activities/" + RUN + "/live");
    }

    @Test
    void aFinishedRunLeadsToTheRunItself() {
        dispatch.runFinished(RUN, MARIE, AudienceScope.PUBLIC, AT);

        assertThat(broadcaster.delivered().getFirst().deepLink()).isEqualTo("/activities/" + RUN);
        assertThat(broadcaster.delivered().getFirst().type())
                .isEqualTo(NotificationType.FRIEND_FINISHED_ACTIVITY);
    }

    @Test
    void anEffectivelyPrivateRunNotifiesNobody() {
        dispatch.runStarted(RUN, MARIE, AudienceScope.PRIVATE, AT);

        assertThat(notifications.size()).isZero();
        assertThat(broadcaster.delivered()).isEmpty();
    }

    @Test
    void aFollowerWhoMutedThatNatureIsSkipped() {
        preferences.save(NotificationPreferences.everythingOn(PAUL)
                .mute(Set.of(NotificationType.FRIEND_STARTED_ACTIVITY)));

        dispatch.runStarted(RUN, MARIE, AudienceScope.PUBLIC, AT);

        assertThat(broadcaster.delivered())
                .extracting(Notification::recipientId)
                .containsExactly(LEA);
    }

    /** Couper une nature n'en coupe pas une autre. */
    @Test
    void mutingOneNatureLeavesTheOthersAlone() {
        preferences.save(NotificationPreferences.everythingOn(PAUL)
                .mute(Set.of(NotificationType.FRIEND_STARTED_ACTIVITY)));

        dispatch.runFinished(RUN, MARIE, AudienceScope.PUBLIC, AT);

        assertThat(broadcaster.delivered())
                .extracting(Notification::recipientId)
                .containsExactlyInAnyOrder(PAUL, LEA);
    }

    /**
     * L'exigence d'idempotence du §7 : le registre rejoue au redémarrage ce qui n'avait pas
     * abouti, et ce rejeu ne doit ni écrire ni pousser une seconde fois.
     */
    @Test
    void replayingTheSameEventNotifiesNobodyTwice() {
        dispatch.runStarted(RUN, MARIE, AudienceScope.PUBLIC, AT);
        int afterFirst = notifications.size();
        int pushedFirst = broadcaster.delivered().size();

        dispatch.runStarted(RUN, MARIE, AudienceScope.PUBLIC, AT);

        assertThat(notifications.size()).isEqualTo(afterFirst);
        assertThat(broadcaster.delivered()).hasSize(pushedFirst);
    }

    /** Un abonnement accepté se raconte des deux côtés, et pas de la même façon. */
    @Test
    void anAcceptedFollowTellsBothSides() {
        dispatch.followAccepted(PAUL, MARIE, AT);

        assertThat(broadcaster.delivered())
                .extracting(Notification::recipientId, Notification::type)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(MARIE, NotificationType.NEW_FOLLOWER),
                        org.assertj.core.groups.Tuple.tuple(PAUL, NotificationType.FOLLOW_ACCEPTED));
    }

    @Test
    void aFollowRequestLandsInTheRequestsScreen() {
        dispatch.followRequested(PAUL, MARIE, AT);

        assertThat(broadcaster.delivered()).singleElement().satisfies(notification -> {
            assertThat(notification.recipientId()).isEqualTo(MARIE);
            assertThat(notification.type()).isEqualTo(NotificationType.FOLLOW_REQUEST);
            assertThat(notification.deepLink()).isEqualTo("/me/follow-requests");
        });
    }

    @Test
    void replayingAFollowNotifiesNobodyTwice() {
        dispatch.followRequested(PAUL, MARIE, AT);
        dispatch.followRequested(PAUL, MARIE, AT);

        assertThat(notifications.size()).isEqualTo(1);
        assertThat(broadcaster.delivered()).hasSize(1);
    }

    @Test
    void aRecipientWhoMutedFollowRequestsIsLeftAlone() {
        preferences.save(NotificationPreferences.everythingOn(MARIE)
                .mute(Set.of(NotificationType.FOLLOW_REQUEST)));

        dispatch.followRequested(PAUL, MARIE, AT);

        assertThat(notifications.size()).isZero();
    }
}
