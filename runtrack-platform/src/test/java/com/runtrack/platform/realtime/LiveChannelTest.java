package com.runtrack.platform.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * L'établissement d'une connexion et la publication, sans Dragonfly joignable.
 *
 * <p>Deux chemins que rien ne parcourt jamais par accident se croisent ici : le journal
 * incapable de promettre une reprise — donc le retour à l'instantané — et la panne du direct,
 * qui ne doit sous aucun prétexte remonter à l'appelant.
 */
class LiveChannelTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-29T08:00:00Z"), ZoneOffset.UTC);
    private static final String TOPIC = "live:activity:one:events";

    private SseRegistry registry;
    private SimpleMeterRegistry meters;

    @BeforeEach
    void setUp() {
        meters = new SimpleMeterRegistry();
        registry = new SseRegistry(new NoopWatcher(), CLOCK, meters);
    }

    private LiveChannel channelWithQueueOf(int capacity) {
        return new LiveChannel(
                registry,
                new StreamLog(new UnreachableRedis()),
                new UnreachableRedis(),
                new RealtimeProperties(null, null, null, null, capacity, null),
                meters);
    }

    private static final class NoopWatcher implements StreamWatcher {

        @Override
        public void watch(String topic, Consumer<PublishedEvent> sink) {
        }

        @Override
        public void unwatch(String topic) {
        }
    }

    private static List<PublishedEvent> snapshotOf(int size) {
        return IntStream.range(0, size)
                .mapToObj(index -> PublishedEvent.withoutId("status", "{\"n\":" + index + "}"))
                .toList();
    }

    @Test
    void aSubscriberIsRegisteredAndStaysConnected() {
        channelWithQueueOf(16).subscribe(TOPIC, Optional.empty(), () -> snapshotOf(3));

        assertThat(registry.count()).isEqualTo(1);
    }

    /**
     * Un instantané qui ne tient pas dans la file donnerait un état amputé sans que le client
     * puisse le savoir. Mieux vaut le renvoyer se reconnecter.
     */
    @Test
    void aSnapshotThatDoesNotFitSendsTheSubscriberAway() {
        channelWithQueueOf(2).subscribe(TOPIC, Optional.empty(), () -> snapshotOf(10));

        assertThat(registry.count()).isZero();
    }

    /** Un Last-Event-ID qu'on ne peut pas honorer ne fait pas échouer : il fait revenir à zéro. */
    @Test
    void anUnusableLastEventIdFallsBackToTheSnapshot() {
        channelWithQueueOf(16).subscribe(TOPIC, Optional.of("1700000000000-0"), () -> snapshotOf(3));

        assertThat(registry.count()).isEqualTo(1);
    }

    @Test
    void anUnreachableDragonflyNeverFailsThePublisher() {
        assertThatCode(() -> channelWithQueueOf(16).publish(TOPIC, snapshotOf(2)))
                .doesNotThrowAnyException();

        assertThat(meters.counter("runtrack.live.events.failed").count()).isEqualTo(2);
        assertThat(meters.counter("runtrack.live.events.published").count()).isZero();
    }

    @Test
    void closingATopicThatCannotBeReachedIsNotAnError() {
        assertThatCode(() -> channelWithQueueOf(16).close(TOPIC)).doesNotThrowAnyException();
    }

    /** Rien à dire, rien à écrire : pas d'aller-retour pour une diffusion vide. */
    @Test
    void anEmptyBroadcastTouchesNothing() {
        channelWithQueueOf(16).publish(TOPIC, List.of());

        assertThat(meters.counter("runtrack.live.events.failed").count()).isZero();
    }
}
