package com.runtrack.notification.usecases.model.push;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.runtrack.shared.id.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeviceTokenTest {

    private static final UserId MARIE = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000001"));

    @Test
    void aPlausibleTokenIsAccepted() {
        assertThatCode(() -> new DeviceToken("f".repeat(160), MARIE, DevicePlatform.IOS, Instant.EPOCH))
                .doesNotThrowAnyException();
    }

    /** Un appareil sans jeton n'est joignable par personne : le stocker ne servirait qu'à le retenter. */
    @Test
    void aDeviceWithoutATokenIsRefused() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(
                () -> new DeviceToken("  ", MARIE, DevicePlatform.ANDROID, Instant.EPOCH));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(
                () -> new DeviceToken(null, MARIE, DevicePlatform.ANDROID, Instant.EPOCH));
    }

    /** La borne est celle de la colonne : au-delà, l'insertion échouerait plus loin et plus tard. */
    @Test
    void anImplausiblyLongTokenIsRefusedBeforeItReachesTheDatabase() {
        String tooLong = "f".repeat(DeviceToken.MAX_TOKEN_LENGTH + 1);

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(
                () -> new DeviceToken(tooLong, MARIE, DevicePlatform.ANDROID, Instant.EPOCH));
    }

    @Test
    void anIncompleteDeviceIsRefused() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(
                () -> new DeviceToken("abc", null, DevicePlatform.ANDROID, Instant.EPOCH));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(
                () -> new DeviceToken("abc", MARIE, null, Instant.EPOCH));
    }
}
