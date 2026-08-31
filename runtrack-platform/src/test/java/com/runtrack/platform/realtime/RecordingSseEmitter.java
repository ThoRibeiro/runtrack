package com.runtrack.platform.realtime;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Un émetteur SSE qui note ce qu'on lui envoie au lieu de l'écrire sur une socket.
 *
 * <p>Sous-classe plutôt que serveur de test : ce qui est vérifié ici, c'est l'ordre et le
 * cadencement des envois, pas le format du protocole — celui-là est l'affaire du test de bout
 * en bout, qui parle à un vrai serveur.
 */
final class RecordingSseEmitter extends SseEmitter {

    private final List<String> sent = new CopyOnWriteArrayList<>();
    private final AtomicBoolean completed = new AtomicBoolean();
    private volatile CountDownLatch expected = new CountDownLatch(0);

    @Override
    public void send(SseEventBuilder builder) throws IOException {
        var text = new StringBuilder();
        builder.build().forEach(part -> text.append(part.getData()));
        sent.add(text.toString());
        expected.countDown();
    }

    @Override
    public void complete() {
        completed.set(true);
    }

    @Override
    public void completeWithError(Throwable failure) {
        completed.set(true);
    }

    /** Arme l'attente avant de déclencher les envois, sinon la course est perdue d'avance. */
    void expecting(int sends) {
        expected = new CountDownLatch(sends);
    }

    void awaitExpected() throws InterruptedException {
        if (!expected.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Envois attendus jamais arrivés, reçus : " + sent);
        }
    }

    List<String> sent() {
        return List.copyOf(sent);
    }

    boolean isCompleted() {
        return completed.get();
    }
}
