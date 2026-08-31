package com.runtrack.notification.usecases.model.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** La plage de silence, et le piège qu'elle tend : elle traverse minuit dans le cas usuel. */
class QuietHoursTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final ZoneId NOUMEA = ZoneId.of("Pacific/Noumea");

    private static QuietHours night(ZoneId zone) {
        return new QuietHours(LocalTime.of(22, 0), LocalTime.of(7, 0), zone);
    }

    /** 22 h → 7 h : la plage est l'union de « après 22 h » et « avant 7 h », pas un encadrement. */
    @Test
    void aRangeCrossingMidnightCoversBothSidesOfIt() {
        QuietHours quiet = night(PARIS);

        assertThat(quiet.covers(Instant.parse("2026-08-31T21:00:00Z"))).isTrue();  // 23 h à Paris
        assertThat(quiet.covers(Instant.parse("2026-08-31T02:00:00Z"))).isTrue();  // 4 h à Paris
        assertThat(quiet.covers(Instant.parse("2026-08-31T12:00:00Z"))).isFalse(); // 14 h à Paris
    }

    /** Une plage qui ne traverse pas minuit reste un encadrement ordinaire. */
    @Test
    void aDaytimeRangeIsAPlainInterval() {
        QuietHours meeting = new QuietHours(LocalTime.of(9, 0), LocalTime.of(11, 0), PARIS);

        assertThat(meeting.covers(Instant.parse("2026-08-31T08:00:00Z"))).isTrue();  // 10 h
        assertThat(meeting.covers(Instant.parse("2026-08-31T12:00:00Z"))).isFalse(); // 14 h
    }

    /**
     * Le fuseau est ce qui donne son sens à la plage.
     *
     * <p>Au même instant, il est 23 h à Paris et 8 h à Nouméa : le premier dort, le second est
     * debout. Sans fuseau enregistré, l'un des deux serait réveillé.
     */
    @Test
    void theSameInstantIsQuietSomewhereAndNotElsewhere() {
        Instant moment = Instant.parse("2026-08-31T21:00:00Z");

        assertThat(night(PARIS).covers(moment)).isTrue();
        assertThat(night(NOUMEA).covers(moment)).isFalse();
    }

    /** La borne de début est incluse, celle de fin exclue : sinon 7 h pile serait ambigu. */
    @Test
    void theStartIsIncludedAndTheEndIsNot() {
        QuietHours quiet = night(ZoneId.of("UTC"));

        assertThat(quiet.covers(Instant.parse("2026-08-31T22:00:00Z"))).isTrue();
        assertThat(quiet.covers(Instant.parse("2026-08-31T07:00:00Z"))).isFalse();
    }

    /** Deux bornes égales décriraient aussi bien zéro heure que vingt-quatre : on refuse. */
    @Test
    void aRangeOfZeroLengthIsRefusedRatherThanGuessed() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(
                () -> new QuietHours(LocalTime.of(22, 0), LocalTime.of(22, 0), PARIS));
    }

    @Test
    void aRangeWithoutAZoneIsRefused() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(
                () -> new QuietHours(LocalTime.of(22, 0), LocalTime.of(7, 0), null));
    }
}
