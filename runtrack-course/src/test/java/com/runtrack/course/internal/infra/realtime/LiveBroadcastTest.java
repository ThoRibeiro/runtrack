package com.runtrack.course.internal.infra.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.runtrack.course.internal.domain.live.LiveEvent;
import com.runtrack.shared.id.ActivityId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * L'établissement d'une connexion, sans Dragonfly.
 *
 * <p>Le journal est ici injoignable, donc incapable de promettre une reprise : c'est exactement
 * ce qui fait retomber sur l'instantané, et c'est ce chemin-là qu'on veut voir.
 */
class LiveBroadcastTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-29T08:00:00Z"), ZoneOffset.UTC);
    private static final Instant AT = Instant.parse("2026-08-29T08:00:00Z");

    private LiveEmitterRegistry registry;
    private ActivityId run;

    private LiveBroadcast broadcastWithQueueOf(int capacity) {
        return new LiveBroadcast(
                registry,
                new LiveEventLog(new UnreachableRedis()),
                new LiveEventCodec(new ObjectMapper()),
                new RealtimeProperties(null, null, null, null, capacity, null));
    }

    @BeforeEach
    void setUp() {
        registry = new LiveEmitterRegistry(new NoopWatcher(), CLOCK, new SimpleMeterRegistry());
        run = ActivityId.generate(CLOCK, new Random(2));
    }

    private static final class NoopWatcher implements LiveStreamWatcher {

        @Override
        public void watch(ActivityId activityId, Consumer<RecordedEvent> sink) {
        }

        @Override
        public void unwatch(ActivityId activityId) {
        }
    }

    private static List<LiveEvent> snapshotOf(int size) {
        return IntStream.range(0, size)
                .mapToObj(index -> (LiveEvent) new LiveEvent.Status("Live", AT))
                .toList();
    }

    @Test
    void aSpectatorIsRegisteredAndStaysConnected() {
        broadcastWithQueueOf(16).follow(run, Optional.empty(), () -> snapshotOf(3));

        assertThat(registry.count()).isEqualTo(1);
    }

    /**
     * Un instantané qui ne tient pas dans la file donnerait un tracé amputé sans que le client
     * puisse le savoir. Mieux vaut le renvoyer se reconnecter.
     */
    @Test
    void aSnapshotThatDoesNotFitSendsTheSpectatorAway() {
        broadcastWithQueueOf(2).follow(run, Optional.empty(), () -> snapshotOf(10));

        assertThat(registry.count()).isZero();
    }

    /** Un Last-Event-ID qu'on ne peut pas honorer ne fait pas échouer : il fait revenir à zéro. */
    @Test
    void anUnusableLastEventIdFallsBackToTheSnapshot() {
        broadcastWithQueueOf(16).follow(run, Optional.of("1700000000000-0"), () -> snapshotOf(3));

        assertThat(registry.count()).isEqualTo(1);
    }
}
