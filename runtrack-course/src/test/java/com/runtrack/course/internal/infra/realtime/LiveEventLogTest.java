package com.runtrack.course.internal.infra.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.runtrack.shared.id.ActivityId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Random;
import org.junit.jupiter.api.Test;

class LiveEventLogTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-29T08:00:00Z"), ZoneOffset.UTC);

    /**
     * Un journal injoignable ne peut rien garantir : il le dit, et le spectateur repart d'un
     * instantané. Rendre une liste vide reviendrait à affirmer « tu n'as rien manqué ».
     */
    @Test
    void anUnreachableLogCannotPromiseContinuity() {
        var log = new LiveEventLog(new UnreachableRedis());

        assertThat(log.replayAfter(ActivityId.generate(CLOCK, new Random(1)), "1700000000000-0"))
                .isEmpty();
    }
}
