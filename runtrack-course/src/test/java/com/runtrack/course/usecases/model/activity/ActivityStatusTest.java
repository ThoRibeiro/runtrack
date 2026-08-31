package com.runtrack.course.usecases.model.activity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ActivityStatusTest {

    private static final Instant NOW = Instant.parse("2026-08-29T08:00:00Z");

    @Test
    void onlyALiveActivityTakesPoints() {
        assertThat(new ActivityStatus.Live(NOW).acceptsPoints()).isTrue();
        assertThat(new ActivityStatus.Paused(NOW).acceptsPoints()).isFalse();
        assertThat(new ActivityStatus.Finished(NOW).acceptsPoints()).isFalse();
        assertThat(new ActivityStatus.Discarded(NOW).acceptsPoints()).isFalse();
    }

    @Test
    void finishedAndDiscardedAreTerminal() {
        assertThat(new ActivityStatus.Live(NOW).isTerminal()).isFalse();
        assertThat(new ActivityStatus.Paused(NOW).isTerminal()).isFalse();
        assertThat(new ActivityStatus.Finished(NOW).isTerminal()).isTrue();
        assertThat(new ActivityStatus.Discarded(NOW).isTerminal()).isTrue();
    }

    @Test
    void everyStateRemembersWhenItStarted() {
        assertThat(new ActivityStatus.Live(NOW).since()).isEqualTo(NOW);
        assertThat(new ActivityStatus.Paused(NOW).since()).isEqualTo(NOW);
        assertThat(new ActivityStatus.Finished(NOW).since()).isEqualTo(NOW);
        assertThat(new ActivityStatus.Discarded(NOW).since()).isEqualTo(NOW);
    }
}
