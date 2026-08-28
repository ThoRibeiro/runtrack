package com.runtrack.shared.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class IdentifiersTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-29T10:15:30Z"), ZoneOffset.UTC);
    private static final RandomGenerator RANDOM = new java.util.Random(1);

    @Test
    void userIdRejectsNull() {
        assertThatIllegalArgumentException().isThrownBy(() -> new UserId(null));
    }

    @Test
    void activityIdRejectsNull() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ActivityId(null));
    }

    @Test
    void userIdParsesAndPrintsItsValue() {
        UUID value = UUID.fromString("018f4c1e-0000-7000-8000-000000000001");

        UserId id = UserId.of(value.toString());

        assertThat(id.value()).isEqualTo(value);
        assertThat(id).hasToString(value.toString());
    }

    @Test
    void activityIdParsesAndPrintsItsValue() {
        UUID value = UUID.fromString("018f4c1e-0000-7000-8000-000000000002");

        ActivityId id = ActivityId.of(value.toString());

        assertThat(id.value()).isEqualTo(value);
        assertThat(id).hasToString(value.toString());
    }

    @Test
    void userIdReportsWhatItFailedToParse() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> UserId.of("pas-un-uuid"))
                .withMessageContaining("pas-un-uuid");
    }

    @Test
    void activityIdReportsWhatItFailedToParse() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ActivityId.of("pas-un-uuid"))
                .withMessageContaining("pas-un-uuid");
    }

    @Test
    void generatedIdsAreVersionSeven() {
        assertThat(UserId.generate(CLOCK, RANDOM).value().version()).isEqualTo(7);
        assertThat(ActivityId.generate(CLOCK, RANDOM).value().version()).isEqualTo(7);
    }
}
