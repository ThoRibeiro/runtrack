package com.runtrack.course.internal.infra.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.runtrack.shared.id.ActivityId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Le registre local, et surtout l'invariant qu'il porte : une instance suit une course tant
 * qu'elle a un spectateur pour elle, ni avant ni après.
 */
class LiveEmitterRegistryTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-29T08:00:00Z"), ZoneOffset.UTC);

    /**
     * Compte les abonnements, et refuse d'en ouvrir deux à la fois sur la même course.
     *
     * <p>C'est ce refus qui donne son sens au test de concurrence : sans lui, deux abonnements
     * simultanés passeraient inaperçus et doubleraient silencieusement chaque événement reçu.
     */
    private static final class RecordingWatcher implements LiveStreamWatcher {

        private final Set<ActivityId> active = ConcurrentHashMap.newKeySet();
        private final Map<ActivityId, Consumer<RecordedEvent>> sinks = new ConcurrentHashMap<>();
        private final AtomicInteger watches = new AtomicInteger();
        private final AtomicInteger unwatches = new AtomicInteger();
        private final List<String> violations = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public void watch(ActivityId activityId, Consumer<RecordedEvent> sink) {
            if (!active.add(activityId)) {
                violations.add("Deux abonnements simultanés sur " + activityId);
            }
            sinks.put(activityId, sink);
            watches.incrementAndGet();
        }

        @Override
        public void unwatch(ActivityId activityId) {
            if (!active.remove(activityId)) {
                violations.add("Désabonnement sans abonnement sur " + activityId);
            }
            unwatches.incrementAndGet();
        }
    }

    private RecordingWatcher watcher;
    private LiveEmitterRegistry registry;
    private ActivityId run;

    @BeforeEach
    void setUp() {
        watcher = new RecordingWatcher();
        registry = new LiveEmitterRegistry(watcher, CLOCK, new SimpleMeterRegistry());
        run = ActivityId.generate(CLOCK, new Random(1));
    }

    private static RecordedEvent anEvent() {
        return new RecordedEvent("1700000000000-1", "position", "{\"sequenceNumber\":1}");
    }

    @Test
    void subscribesToTheStreamOnTheFirstSpectatorOnly() {
        registry.register(run, LiveSubscriber.attachedTo(new RecordingSseEmitter(), 4));
        registry.register(run, LiveSubscriber.attachedTo(new RecordingSseEmitter(), 4));

        assertThat(watcher.watches).hasValue(1);
        assertThat(registry.count()).isEqualTo(2);
    }

    @Test
    void unsubscribesWhenTheLastSpectatorLeaves() {
        var first = LiveSubscriber.attachedTo(new RecordingSseEmitter(), 4);
        var second = LiveSubscriber.attachedTo(new RecordingSseEmitter(), 4);
        registry.register(run, first);
        registry.register(run, second);

        registry.deregister(run, first);
        assertThat(watcher.unwatches).hasValue(0);

        registry.deregister(run, second);
        assertThat(watcher.unwatches).hasValue(1);
        assertThat(registry.count()).isZero();
    }

    @Test
    void anEventReachesEverySpectatorOfThatRun() throws Exception {
        var watching = new RecordingSseEmitter();
        var alsoWatching = new RecordingSseEmitter();
        watching.expecting(1);
        alsoWatching.expecting(1);
        var first = LiveSubscriber.attachedTo(watching, 4);
        var second = LiveSubscriber.attachedTo(alsoWatching, 4);
        registry.register(run, first);
        registry.register(run, second);
        first.startPumping();
        second.startPumping();

        registry.broadcast(run, anEvent());

        watching.awaitExpected();
        alsoWatching.awaitExpected();
    }

    @Test
    void broadcastingToARunNobodyWatchesIsHarmless() {
        registry.broadcast(run, anEvent());

        assertThat(registry.count()).isZero();
    }

    /** §4 : le client lent est déconnecté, pas mis en attente — et le registre le range. */
    @Test
    void aSpectatorWhoCannotKeepUpIsDroppedAndUnsubscribed() {
        var emitter = new RecordingSseEmitter();
        var slow = LiveSubscriber.attachedTo(emitter, 1);
        registry.register(run, slow);

        registry.broadcast(run, anEvent());
        registry.broadcast(run, anEvent());

        assertThat(slow.isClosed()).isTrue();
        assertThat(emitter.isCompleted()).isTrue();
        assertThat(registry.count()).isZero();
        assertThat(watcher.unwatches).hasValue(1);
    }

    @Test
    void theHeartbeatReachesEverySpectator() throws Exception {
        var emitter = new RecordingSseEmitter();
        emitter.expecting(1);
        var subscriber = LiveSubscriber.attachedTo(emitter, 4);
        registry.register(run, subscriber);
        subscriber.startPumping();

        registry.heartbeat();

        emitter.awaitExpected();
        assertThat(emitter.sent().getFirst()).contains("event:heartbeat");
    }

    /**
     * L'arrêt gracieux du pod : chaque émetteur est refermé proprement, chaque abonnement rendu.
     */
    @Test
    void stoppingClosesEveryEmitterAndReleasesEveryStream() {
        var emitter = new RecordingSseEmitter();
        registry.register(run, LiveSubscriber.attachedTo(emitter, 4));

        registry.stop();

        assertThat(emitter.isCompleted()).isTrue();
        assertThat(registry.count()).isZero();
        assertThat(registry.isRunning()).isFalse();
        assertThat(watcher.unwatches).hasValue(1);
    }

    /**
     * Arrivées et départs en rafale sur les mêmes courses.
     *
     * <p>Le registre est touché par le fil de chaque requête HTTP <em>et</em> par celui qui
     * relaie le stream ; l'enchaînement « premier arrivé, on s'abonne / dernier parti, on se
     * désabonne » n'a de valeur que s'il tient sous cette concurrence-là.
     */
    @Test
    void survivesConcurrentArrivalsAndDepartures() throws Exception {
        List<ActivityId> runs = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            runs.add(ActivityId.generate(CLOCK, new Random(index)));
        }
        int spectators = 400;
        var ready = new CountDownLatch(1);
        var done = new CountDownLatch(spectators);

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int index = 0; index < spectators; index++) {
                ActivityId watched = runs.get(index % runs.size());
                pool.execute(() -> {
                    try {
                        ready.await();
                        var subscriber = LiveSubscriber.attachedTo(new RecordingSseEmitter(), 8);
                        registry.register(watched, subscriber);
                        registry.broadcast(watched, anEvent());
                        registry.deregister(watched, subscriber);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            ready.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(watcher.violations).isEmpty();
        assertThat(registry.count()).isZero();
        assertThat(watcher.watches).hasValue(watcher.unwatches.get());
    }
}
