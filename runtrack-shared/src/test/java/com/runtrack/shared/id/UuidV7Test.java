package com.runtrack.shared.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class UuidV7Test {

    private static final RandomGenerator FIXED = new java.util.Random(42);

    @Test
    void carriesVersionSevenAndTheRfcVariant() {
        UUID uuid = UuidV7.from(Instant.parse("2026-08-29T10:15:30Z"), FIXED);

        assertThat(uuid.version()).isEqualTo(7);
        assertThat(uuid.variant()).isEqualTo(2);
    }

    @Test
    void encodesTheTimestampInTheLeadingBits() {
        Instant when = Instant.parse("2026-08-29T10:15:30.123Z");

        long encoded = UuidV7.from(when, FIXED).getMostSignificantBits() >>> 16;

        assertThat(encoded).isEqualTo(when.toEpochMilli());
    }

    /** L'intérêt de la v7 sur la v4 : l'ordre lexicographique suit l'ordre temporel. */
    @Test
    void sortsInTimeOrder() {
        UUID earlier = UuidV7.from(Instant.parse("2026-08-29T10:00:00Z"), FIXED);
        UUID later = UuidV7.from(Instant.parse("2026-08-29T11:00:00Z"), FIXED);

        assertThat(earlier.toString()).isLessThan(later.toString());
    }

    @Test
    void refusesDatesBeforeTheEpoch() {
        Instant beforeEpoch = Instant.parse("1969-12-31T23:59:59Z");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> UuidV7.from(beforeEpoch, FIXED))
                .withMessageContaining("antérieure à l'epoch");
    }
}
