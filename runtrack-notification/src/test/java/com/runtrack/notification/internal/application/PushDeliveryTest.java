package com.runtrack.notification.internal.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.runtrack.notification.internal.application.fixture.NotificationDoubles;
import com.runtrack.notification.internal.domain.inbox.DeepLink;
import com.runtrack.notification.internal.domain.inbox.Notification;
import com.runtrack.notification.internal.domain.inbox.NotificationId;
import com.runtrack.notification.internal.domain.inbox.NotificationPreferences;
import com.runtrack.notification.internal.domain.inbox.NotificationType;
import com.runtrack.notification.internal.domain.push.DevicePlatform;
import com.runtrack.notification.internal.domain.push.DeviceToken;
import com.runtrack.notification.internal.domain.push.QuietHours;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import com.runtrack.user.UserSummary;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** L'envoi des push : qui reçoit, qui dort, qui en a déjà eu un, et quels jetons sont morts. */
class PushDeliveryTest {

    private static final UserId MARIE = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000001"));
    private static final UserId PAUL = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000002"));
    private static final UserId LEA = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000003"));
    private static final ActivityId RUN = new ActivityId(UUID.fromString("018f4c1e-0000-7000-8000-0000000000ff"));

    /** 14 h à Paris : personne ne dort, sauf si le test le décide. */
    private static final Instant MIDDAY = Instant.parse("2026-08-31T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(MIDDAY, ZoneOffset.UTC);

    private NotificationDoubles.Devices devices;
    private NotificationDoubles.Preferences preferences;
    private NotificationDoubles.Sender sender;
    private NotificationDoubles.Throttle throttle;
    private PushDelivery delivery;

    @BeforeEach
    void setUp() {
        devices = new NotificationDoubles.Devices();
        preferences = new NotificationDoubles.Preferences();
        sender = new NotificationDoubles.Sender();
        throttle = new NotificationDoubles.Throttle();
        delivery = new PushDelivery(devices, preferences, throttle, sender, new NamingUsers(), CLOCK);
    }

    /** Ne répond qu'à la seule question que le push pose : comment s'appelle l'acteur. */
    private static final class NamingUsers extends NotificationDoubles.Users {

        @Override
        public java.util.Map<UserId, UserSummary> summaries(java.util.Collection<UserId> ids) {
            return ids.stream().collect(java.util.stream.Collectors.toMap(
                    id -> id,
                    id -> new UserSummary(id, "marie", "Marie", Optional.empty())));
        }
    }

    private void deviceOf(UserId owner, String token) {
        devices.register(new DeviceToken(token, owner, DevicePlatform.ANDROID, MIDDAY));
    }

    private static Notification runStartedFor(UserId recipient) {
        return Notification.unread(
                NotificationId.deducedFrom(
                        NotificationType.FRIEND_STARTED_ACTIVITY, recipient, RUN.toString(), MIDDAY),
                recipient, NotificationType.FRIEND_STARTED_ACTIVITY, MARIE,
                DeepLink.liveTracking(RUN), MIDDAY);
    }

    /**
     * Le « jamais un appel par ami » du §7 : un fan-out produit un seul message, donc un seul
     * envoi, quel que soit le nombre d'abonnés.
     */
    @Test
    void oneFanOutIsOneSendNotOnePerFriend() {
        deviceOf(PAUL, "token-paul");
        deviceOf(LEA, "token-lea");

        delivery.push(List.of(runStartedFor(PAUL), runStartedFor(LEA)));

        assertThat(sender.sent()).hasSize(1);
        assertThat(sender.sent().getFirst().devices())
                .extracting(DeviceToken::token)
                .containsExactlyInAnyOrder("token-paul", "token-lea");
        assertThat(sender.sent().getFirst().message().title())
                .isEqualTo("Marie vient de démarrer une course");
    }

    /** Un destinataire sans appareil déclaré ne déclenche aucun envoi. */
    @Test
    void aRecipientWithoutAnyDeviceCostsNothing() {
        delivery.push(List.of(runStartedFor(PAUL)));

        assertThat(sender.sent()).isEmpty();
    }

    /** §7 : au plus un push par couple et par nature sur la fenêtre glissante. */
    @Test
    void theSameRunnerDoesNotPushTwiceWithinTheWindow() {
        deviceOf(PAUL, "token-paul");

        delivery.push(List.of(runStartedFor(PAUL)));
        delivery.push(List.of(runStartedFor(PAUL)));

        assertThat(sender.sent()).hasSize(1);
    }

    /** Le garde-fou est par couple : ce qu'un coureur consomme ne bloque pas les autres. */
    @Test
    void theGuardIsPerPairNotGlobal() {
        deviceOf(PAUL, "token-paul");
        deviceOf(LEA, "token-lea");

        delivery.push(List.of(runStartedFor(PAUL)));
        delivery.push(List.of(runStartedFor(LEA)));

        assertThat(sender.sent()).hasSize(2);
    }

    /** Les heures calmes coupent le push, jamais l'écriture — la notification, elle, est déjà là. */
    @Test
    void aSleepingRecipientIsNotWokenUp() {
        deviceOf(PAUL, "token-paul");
        preferences.save(NotificationPreferences.everythingOn(PAUL).quietBetween(Optional.of(
                new QuietHours(LocalTime.of(0, 0), LocalTime.of(23, 59), ZoneId.of("UTC")))));

        delivery.push(List.of(runStartedFor(PAUL)));

        assertThat(sender.sent()).isEmpty();
    }

    @Test
    void aRecipientOutsideTheirQuietHoursStillGetsThePush() {
        deviceOf(PAUL, "token-paul");
        preferences.save(NotificationPreferences.everythingOn(PAUL).quietBetween(Optional.of(
                new QuietHours(LocalTime.of(22, 0), LocalTime.of(7, 0), ZoneId.of("UTC")))));

        delivery.push(List.of(runStartedFor(PAUL)));

        assertThat(sender.sent()).hasSize(1);
    }

    /** Un jeton que Firebase déclare mort est effacé : sinon on le retente à chaque fan-out. */
    @Test
    void tokensRejectedByTheServiceArePurged() {
        deviceOf(PAUL, "token-paul");
        deviceOf(LEA, "token-lea");
        sender.rejecting("token-paul");

        delivery.push(List.of(runStartedFor(PAUL), runStartedFor(LEA)));

        assertThat(devices.of(PAUL)).isEmpty();
        assertThat(devices.of(LEA)).hasSize(1);
    }

    @Test
    void nothingToPushMeansNoCallAtAll() {
        delivery.push(List.of());

        assertThat(sender.sent()).isEmpty();
    }

    /** Deux natures différentes du même coureur sont deux messages, donc deux envois. */
    @Test
    void twoDifferentNaturesAreTwoMessages() {
        deviceOf(PAUL, "token-paul");
        Notification finished = Notification.unread(
                NotificationId.deducedFrom(
                        NotificationType.FRIEND_FINISHED_ACTIVITY, PAUL, RUN.toString(), MIDDAY),
                PAUL, NotificationType.FRIEND_FINISHED_ACTIVITY, MARIE, DeepLink.activity(RUN), MIDDAY);

        delivery.push(List.of(runStartedFor(PAUL), finished));

        assertThat(sender.sent()).hasSize(2);
    }
}
