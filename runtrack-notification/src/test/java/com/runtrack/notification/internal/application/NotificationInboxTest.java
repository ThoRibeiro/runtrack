package com.runtrack.notification.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.runtrack.notification.internal.application.fixture.NotificationDoubles;
import com.runtrack.notification.internal.domain.inbox.DeepLink;
import com.runtrack.notification.internal.domain.inbox.Notification;
import com.runtrack.notification.internal.domain.inbox.NotificationId;
import com.runtrack.notification.internal.domain.inbox.NotificationType;
import com.runtrack.shared.error.NotFoundException;
import com.runtrack.shared.id.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationInboxTest {

    private static final UserId MARIE = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000001"));
    private static final UserId PAUL = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000002"));
    private static final Instant AT = Instant.parse("2026-08-29T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(AT.plusSeconds(60), ZoneOffset.UTC);

    private NotificationDoubles.Notifications notifications;
    private NotificationInbox inbox;

    @BeforeEach
    void setUp() {
        notifications = new NotificationDoubles.Notifications();
        inbox = new NotificationInbox(notifications, CLOCK);
    }

    private NotificationId store(UserId recipientId, int index) {
        NotificationId id = NotificationId.deducedFrom(
                NotificationType.NEW_FOLLOWER, recipientId, "actor-" + index, AT.plusSeconds(index));
        notifications.appendAll(List.of(Notification.unread(
                id, recipientId, NotificationType.NEW_FOLLOWER, PAUL,
                DeepLink.profile(PAUL), AT.plusSeconds(index))));
        return id;
    }

    @Test
    void readsTheMostRecentFirst() {
        store(MARIE, 1);
        store(MARIE, 2);

        List<Notification> page = inbox.page(MARIE, Optional.empty(), false, null);

        assertThat(page).hasSize(2);
        assertThat(page.getFirst().createdAt()).isAfter(page.getLast().createdAt());
    }

    /** Une boîte n'est jamais celle de quelqu'un d'autre : le destinataire va jusqu'à la requête. */
    @Test
    void neverShowsSomeoneElsesInbox() {
        store(PAUL, 1);

        assertThat(inbox.page(MARIE, Optional.empty(), false, null)).isEmpty();
        assertThat(inbox.unreadCount(MARIE)).isZero();
    }

    @Test
    void marksOneAsReadAndDropsTheUnreadCount() {
        NotificationId id = store(MARIE, 1);
        store(MARIE, 2);

        inbox.markRead(MARIE, id);

        assertThat(inbox.unreadCount(MARIE)).isEqualTo(1);
        assertThat(inbox.page(MARIE, Optional.empty(), true, null)).hasSize(1);
    }

    /** Marquer lu ce qui l'est déjà n'est pas une erreur, et ne déplace pas la date de lecture. */
    @Test
    void markingAnAlreadyReadNotificationIsHarmless() {
        NotificationId id = store(MARIE, 1);
        inbox.markRead(MARIE, id);
        Instant firstRead = notifications.find(MARIE, id).orElseThrow().readAt().orElseThrow();

        inbox.markRead(MARIE, id);

        assertThat(notifications.find(MARIE, id).orElseThrow().readAt()).contains(firstRead);
    }

    @Test
    void markingSomeoneElsesNotificationIsNotFound() {
        NotificationId id = store(PAUL, 1);

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> inbox.markRead(MARIE, id));
    }

    @Test
    void markingEverythingReadReportsHowManyItTouched() {
        store(MARIE, 1);
        store(MARIE, 2);

        assertThat(inbox.markAllRead(MARIE)).isEqualTo(2);
        assertThat(inbox.markAllRead(MARIE)).isZero();
        assertThat(inbox.unreadCount(MARIE)).isZero();
    }

    /** Le curseur du §8 : on repart de la date de la dernière reçue. */
    @Test
    void paginatesByCursor() {
        store(MARIE, 1);
        store(MARIE, 2);
        store(MARIE, 3);

        List<Notification> firstPage = inbox.page(MARIE, Optional.empty(), false, 2);
        List<Notification> secondPage = inbox.page(
                MARIE, Optional.of(firstPage.getLast().createdAt()), false, 2);

        assertThat(firstPage).hasSize(2);
        assertThat(secondPage).singleElement()
                .extracting(Notification::createdAt)
                .isEqualTo(AT.plusSeconds(1));
    }

    /** Une limite délirante est ramenée dans les bornes plutôt que refusée. */
    @Test
    void anAbsurdPageSizeIsClamped() {
        store(MARIE, 1);

        assertThat(inbox.page(MARIE, Optional.empty(), false, 10_000)).hasSize(1);
        assertThat(inbox.page(MARIE, Optional.empty(), false, -5)).hasSize(1);
    }
}
