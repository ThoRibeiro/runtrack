package com.runtrack.notification.usecases.model.inbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.runtrack.shared.id.UserId;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationPreferencesTest {

    private static final UserId MARIE = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000001"));

    /** Le choix de stocker les natures coupées : une nature ajoutée arrive allumée. */
    @Test
    void anythingNotMutedIsAllowed() {
        NotificationPreferences preferences = NotificationPreferences.everythingOn(MARIE)
                .mute(Set.of(NotificationType.FRIEND_STARTED_ACTIVITY));

        assertThat(preferences.allows(NotificationType.FRIEND_STARTED_ACTIVITY)).isFalse();
        assertThat(preferences.allows(NotificationType.NEW_FOLLOWER)).isTrue();
        assertThat(preferences.allows(NotificationType.COMMENT_REPLIED)).isTrue();
    }

    @Test
    void freshPreferencesLetEverythingThrough() {
        NotificationPreferences preferences = NotificationPreferences.everythingOn(MARIE);

        assertThat(NotificationType.values()).allMatch(preferences::allows);
    }

    /** Le PATCH remplace la liste entière : muter à nouveau n'accumule pas. */
    @Test
    void mutingReplacesRatherThanAccumulates() {
        NotificationPreferences preferences = NotificationPreferences.everythingOn(MARIE)
                .mute(Set.of(NotificationType.NEW_FOLLOWER))
                .mute(Set.of(NotificationType.FOLLOW_REQUEST));

        assertThat(preferences.allows(NotificationType.NEW_FOLLOWER)).isTrue();
        assertThat(preferences.allows(NotificationType.FOLLOW_REQUEST)).isFalse();
    }
}
