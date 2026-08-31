package com.runtrack.notification.internal.infra.push;

import static org.assertj.core.api.Assertions.assertThat;

import com.runtrack.notification.internal.application.port.PushSender;
import com.runtrack.notification.internal.domain.push.DevicePlatform;
import com.runtrack.notification.internal.domain.push.DeviceToken;
import com.runtrack.notification.internal.domain.push.PushMessage;
import com.runtrack.shared.id.UserId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LoggingPushSenderTest {

    private static final UserId MARIE = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000001"));

    /**
     * L'envoyeur de développement compte les appareils comme servis, et n'invalide rien.
     *
     * <p>Le second point est ce qui compte : inventer des jetons invalides ferait purger, en local
     * et en test, des appareils parfaitement joignables.
     */
    @Test
    void reportsEverythingAsDeliveredAndNothingAsInvalid() {
        var devices = List.of(
                new DeviceToken("a", MARIE, DevicePlatform.ANDROID, Instant.EPOCH),
                new DeviceToken("b", MARIE, DevicePlatform.IOS, Instant.EPOCH));

        PushSender.Result result = new LoggingPushSender()
                .send(devices, new PushMessage("Titre", "Corps", "/activities/abc/live"));

        assertThat(result.delivered()).isEqualTo(2);
        assertThat(result.invalidTokens()).isEmpty();
    }
}
