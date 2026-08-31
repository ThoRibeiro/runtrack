package com.runtrack.notification.usecases.model.inbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.id.UserId;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationAudienceTest {

    private static final UserId PAUL = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000002"));
    private static final Set<UserId> FOLLOWERS = Set.of(PAUL);

    /** La règle centrale du fan-out : une course effectivement privée ne prévient personne. */
    @Test
    void aPrivateRunNotifiesNobody() {
        assertThat(NotificationAudience.forStartedActivity(AudienceScope.PRIVATE, FOLLOWERS)).isEmpty();
    }

    @Test
    void aPublicOrFollowersRunReachesTheAcceptedFollowers() {
        assertThat(NotificationAudience.forStartedActivity(AudienceScope.PUBLIC, FOLLOWERS))
                .containsExactly(PAUL);
        assertThat(NotificationAudience.forStartedActivity(AudienceScope.FOLLOWERS, FOLLOWERS))
                .containsExactly(PAUL);
    }

    /** Une course publique sans abonné n'a personne à prévenir, et ce n'est pas une erreur. */
    @Test
    void aRunnerWithoutFollowersNotifiesNobody() {
        assertThat(NotificationAudience.forStartedActivity(AudienceScope.PUBLIC, Set.of())).isEmpty();
    }
}
