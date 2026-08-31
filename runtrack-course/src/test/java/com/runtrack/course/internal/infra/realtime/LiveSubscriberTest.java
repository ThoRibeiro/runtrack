package com.runtrack.course.internal.infra.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** La file d'un spectateur : son ordre, et ce qu'elle fait quand elle déborde. */
class LiveSubscriberTest {

    private static RecordedEvent position(int sequence) {
        return new RecordedEvent("1700000000000-" + sequence, "position",
                "{\"sequenceNumber\":" + sequence + "}");
    }

    private static RecordedEvent snapshot(String payload) {
        return RecordedEvent.withoutId("status", payload);
    }

    @Test
    void sendsInTheOrderItReceived() throws Exception {
        var emitter = new RecordingSseEmitter();
        var subscriber = LiveSubscriber.attachedTo(emitter, 10);
        emitter.expecting(3);

        subscriber.offer(position(1));
        subscriber.offer(position(2));
        subscriber.offer(position(3));
        subscriber.startPumping();
        emitter.awaitExpected();

        assertThat(emitter.sent()).hasSize(3);
        assertThat(emitter.sent().getFirst()).contains("\"sequenceNumber\":1");
        assertThat(emitter.sent().getLast()).contains("\"sequenceNumber\":3");
    }

    /**
     * L'invariant qui ferme le trou de la connexion : l'instantané est lu <em>après</em>
     * l'abonnement, donc derrière des événements déjà en file, et doit malgré tout partir devant.
     */
    @Test
    void theBacklogGoesOutBeforeWhatWasAlreadyQueued() throws Exception {
        var emitter = new RecordingSseEmitter();
        var subscriber = LiveSubscriber.attachedTo(emitter, 10);
        emitter.expecting(3);

        subscriber.offer(position(9));
        assertThat(subscriber.offerBacklog(List.of(snapshot("first"), snapshot("second")))).isTrue();
        subscriber.startPumping();
        emitter.awaitExpected();

        assertThat(emitter.sent().get(0)).contains("first");
        assertThat(emitter.sent().get(1)).contains("second");
        assertThat(emitter.sent().get(2)).contains("\"sequenceNumber\":9");
    }

    @Test
    void sendsTheStreamIdentifierOnlyWhenTheEventCameFromTheLog() throws Exception {
        var emitter = new RecordingSseEmitter();
        var subscriber = LiveSubscriber.attachedTo(emitter, 10);
        emitter.expecting(2);

        subscriber.offer(position(1));
        subscriber.offer(snapshot("no-id"));
        subscriber.startPumping();
        emitter.awaitExpected();

        assertThat(emitter.sent().getFirst()).contains("id:1700000000000-1");
        assertThat(emitter.sent().getLast()).doesNotContain("id:");
    }

    /** Un client en retard se voit refuser, jamais mettre en attente : c'est tout le §4. */
    @Test
    void aFullQueueRefusesInsteadOfBlocking() {
        var subscriber = LiveSubscriber.attachedTo(new RecordingSseEmitter(), 2);

        assertThat(subscriber.offer(position(1))).isTrue();
        assertThat(subscriber.offer(position(2))).isTrue();
        assertThat(subscriber.offer(position(3))).isFalse();
    }

    @Test
    void aBacklogThatDoesNotFitIsRefusedWhole() {
        var subscriber = LiveSubscriber.attachedTo(new RecordingSseEmitter(), 2);

        assertThat(subscriber.offerBacklog(List.of(snapshot("a"), snapshot("b"), snapshot("c")))).isFalse();
    }

    @Test
    void aClosedSubscriberTakesNothingMore() {
        var emitter = new RecordingSseEmitter();
        var subscriber = LiveSubscriber.attachedTo(emitter, 10);

        subscriber.complete();

        assertThat(subscriber.isClosed()).isTrue();
        assertThat(subscriber.offer(position(1))).isFalse();
        assertThat(emitter.isCompleted()).isTrue();
    }

    /** Détacher, c'est ranger le spectateur d'un émetteur qui s'est déjà refermé tout seul. */
    @Test
    void detachingLeavesTheEmitterAlone() {
        var emitter = new RecordingSseEmitter();
        var subscriber = LiveSubscriber.attachedTo(emitter, 10);

        subscriber.detach();

        assertThat(subscriber.isClosed()).isTrue();
        assertThat(emitter.isCompleted()).isFalse();
    }
}
