package com.runtrack.course.internal.domain.track;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.runtrack.shared.error.ConflictException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DeviceClockSkewTest {

    private static final Instant SERVER = Instant.parse("2026-08-29T08:00:00Z");

    @Test
    void measuresAPhoneRunningLate() {
        DeviceClockSkew skew = DeviceClockSkew.observe(SERVER.minusSeconds(90), SERVER);

        assertThat(skew.offset()).isEqualTo(Duration.ofSeconds(90));
    }

    @Test
    void measuresAPhoneRunningEarly() {
        DeviceClockSkew skew = DeviceClockSkew.observe(SERVER.plusSeconds(90), SERVER);

        assertThat(skew.offset()).isEqualTo(Duration.ofSeconds(-90));
    }

    /** Une fois la dérive connue, tout horodatage du téléphone se ramène à l'heure serveur. */
    @Test
    void bringsDeviceTimestampsBackToServerTime() {
        DeviceClockSkew skew = DeviceClockSkew.observe(SERVER.minusSeconds(90), SERVER);

        assertThat(skew.correct(SERVER.minusSeconds(90).plusSeconds(30)))
                .isEqualTo(SERVER.plusSeconds(30));
    }

    @Test
    void leavesTimestampsAloneWhenThereIsNoSkew() {
        assertThat(DeviceClockSkew.NONE.correct(SERVER)).isEqualTo(SERVER);
    }

    @Test
    void acceptsSkewRightAtTheLimit() {
        assertThat(DeviceClockSkew.observe(SERVER.minus(DeviceClockSkew.MAX_ACCEPTABLE), SERVER)).isNotNull();
    }

    /**
     * Au-delà du seuil, on refuse de démarrer. Corriger une dérive d'une heure reviendrait
     * à fabriquer des horodatages, et les statistiques qui en sortent seraient fausses
     * sans que rien ne le signale.
     */
    @Test
    void refusesToCorrectAWildlyWrongClock() {
        Instant farOff = SERVER.minus(Duration.ofHours(1));

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> DeviceClockSkew.observe(farOff, SERVER))
                .extracting(ConflictException::code)
                .isEqualTo("DEVICE_CLOCK_TOO_FAR_OFF");
    }

    @Test
    void refusesToBeBuiltWithoutAnOffset() {
        assertThatIllegalArgumentException().isThrownBy(() -> new DeviceClockSkew(null));
    }
}
