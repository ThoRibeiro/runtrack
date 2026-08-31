package com.runtrack.notification.infrastructure.push;

import static org.assertj.core.api.Assertions.assertThat;

import com.runtrack.notification.usecases.model.inbox.NotificationType;
import com.runtrack.shared.id.UserId;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisPushThrottleTest {

    private static final UserId MARIE = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000001"));
    private static final UserId PAUL = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000002"));

    /** Dragonfly injoignable, sans conteneur : la dégradation ne se parcourt pas par hasard. */
    private static final class UnreachableRedis extends StringRedisTemplate {

        @Override
        public ValueOperations<String, String> opsForValue() {
            throw new IllegalStateException("Dragonfly injoignable");
        }
    }

    /**
     * Un garde-fou indisponible laisse passer.
     *
     * <p>Le refuser transformerait une panne de cache en silence complet des notifications. Un push
     * en double est un désagrément ; un destinataire qui ne reçoit plus rien est une panne.
     */
    @Test
    void anUnreachableGuardLetsThePushThrough() {
        var throttle = new RedisPushThrottle(new UnreachableRedis());

        assertThat(throttle.allow(MARIE, PAUL, NotificationType.FRIEND_STARTED_ACTIVITY,
                Duration.ofMinutes(30))).isTrue();
    }
}
