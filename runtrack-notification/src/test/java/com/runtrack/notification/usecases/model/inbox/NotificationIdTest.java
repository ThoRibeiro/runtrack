package com.runtrack.notification.usecases.model.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.runtrack.shared.id.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** L'idempotence du §7 tient entièrement dans cette classe. */
class NotificationIdTest {

    private static final UserId MARIE = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000001"));
    private static final UserId PAUL = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000002"));
    private static final Instant AT = Instant.parse("2026-08-29T08:00:00Z");

    /**
     * Le rejeu au redémarrage repasse le même événement : il doit produire le même identifiant,
     * sans quoi le destinataire verrait la notification deux fois.
     */
    @Test
    void theSameEventAlwaysYieldsTheSameIdentifier() {
        NotificationId first = NotificationId.deducedFrom(
                NotificationType.FRIEND_STARTED_ACTIVITY, MARIE, "course-1", AT);
        NotificationId replayed = NotificationId.deducedFrom(
                NotificationType.FRIEND_STARTED_ACTIVITY, MARIE, "course-1", AT);

        assertThat(replayed).isEqualTo(first);
    }

    @Test
    void twoRecipientsOfTheSameEventGetTheirOwnIdentifier() {
        assertThat(NotificationId.deducedFrom(NotificationType.FRIEND_STARTED_ACTIVITY, MARIE, "c", AT))
                .isNotEqualTo(NotificationId.deducedFrom(
                        NotificationType.FRIEND_STARTED_ACTIVITY, PAUL, "c", AT));
    }

    @Test
    void twoNaturesOnTheSameSubjectDoNotCollide() {
        assertThat(NotificationId.deducedFrom(NotificationType.FRIEND_STARTED_ACTIVITY, MARIE, "c", AT))
                .isNotEqualTo(NotificationId.deducedFrom(
                        NotificationType.FRIEND_FINISHED_ACTIVITY, MARIE, "c", AT));
    }

    /**
     * L'instant vient de l'événement, pas de l'horloge : c'est ce qui laisse deux faits réels se
     * distinguer. Se réabonner après s'être désabonné mérite bien une seconde notification.
     */
    @Test
    void twoDistinctFactsRemainTwoNotifications() {
        assertThat(NotificationId.deducedFrom(NotificationType.NEW_FOLLOWER, MARIE, "paul", AT))
                .isNotEqualTo(NotificationId.deducedFrom(
                        NotificationType.NEW_FOLLOWER, MARIE, "paul", AT.plusSeconds(1)));
    }

    /** Certaines natures n'ont pas de sujet : la nature et le destinataire suffisent. */
    @Test
    void aSubjectlessNotificationStillHasAStableIdentifier() {
        assertThat(NotificationId.deducedFrom(NotificationType.FOLLOW_REQUEST, MARIE, null, AT))
                .isEqualTo(NotificationId.deducedFrom(NotificationType.FOLLOW_REQUEST, MARIE, null, AT));
    }

    @Test
    void anIncompleteEventYieldsNoIdentifier() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(
                () -> NotificationId.deducedFrom(null, MARIE, "c", AT));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(
                () -> NotificationId.deducedFrom(NotificationType.NEW_FOLLOWER, MARIE, "c", null));
    }

    @Test
    void aMalformedIdentifierIsRefusedWithItsValue() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> NotificationId.of("pas-un-uuid"))
                .withMessageContaining("pas-un-uuid");
    }
}
