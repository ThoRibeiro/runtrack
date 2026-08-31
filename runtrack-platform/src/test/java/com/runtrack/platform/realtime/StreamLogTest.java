package com.runtrack.platform.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Random;
import org.junit.jupiter.api.Test;

class StreamLogTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-29T08:00:00Z"), ZoneOffset.UTC);

    /**
     * Un journal injoignable ne peut rien garantir : il le dit, et le spectateur repart d'un
     * instantané. Rendre une liste vide reviendrait à affirmer « tu n'as rien manqué ».
     */
    @Test
    void anUnreachableLogCannotPromiseContinuity() {
        var log = new StreamLog(new UnreachableRedis());

        assertThat(log.replayAfter("live:activity:one:events", "1700000000000-0"))
                .isEmpty();
    }
}
