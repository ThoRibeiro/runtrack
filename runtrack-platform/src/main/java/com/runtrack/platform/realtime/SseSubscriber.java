package com.runtrack.platform.realtime;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Un abonné connecté : son émetteur SSE, et une file bornée devant.
 *
 * <p>La file est ce qui protège le reste. Sans elle, {@code SseEmitter.send} s'exécuterait sur
 * le fil qui relaie le Stream Dragonfly, et un seul abonné sur un réseau lent — un train, un
 * ascenseur — bloquerait la diffusion pour tous les autres abonnés du même sujet.
 *
 * <p>Quand la file déborde, on <b>déconnecte</b> plutôt que d'attendre ou d'agrandir. Un client
 * qui a des minutes de retard ne rattrapera pas ; le laisser accumuler transforme un client lent
 * en fuite mémoire, et le déconnecter lui fait rouvrir une connexion avec son
 * {@code Last-Event-ID}, ce qui est exactement la reprise prévue au §4.
 *
 * <p><b>Une file à deux bouts, et c'est ce qui ferme le trou de la connexion.</b> L'instantané
 * ne peut être lu qu'après l'abonnement au stream — sinon les événements survenus entre la
 * lecture en base et l'abonnement seraient perdus — mais il doit partir <em>en premier</em>.
 * Il est donc inséré en tête pendant que le pompage est encore à l'arrêt, derrière ce qui a déjà
 * pu s'accumuler en queue. Un point peut ainsi arriver deux fois, dans l'instantané puis en
 * direct ; il porte son {@code sequenceNumber}, le client l'ignore. Un doublon se rattrape,
 * un trou ne se voit pas.
 */
final class SseSubscriber {

    private static final Logger LOG = LoggerFactory.getLogger(SseSubscriber.class);

    private final SseEmitter emitter;
    private final BlockingDeque<PublishedEvent> pending;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Thread pump;

    private SseSubscriber(SseEmitter emitter, int queueCapacity) {
        this.emitter = emitter;
        this.pending = new LinkedBlockingDeque<>(queueCapacity);
        this.pump = Thread.ofVirtual().name("live-sse-pump").unstarted(this::drainUntilClosed);
    }

    static SseSubscriber attachedTo(SseEmitter emitter, int queueCapacity) {
        return new SseSubscriber(emitter, queueCapacity);
    }

    /**
     * Un événement en direct.
     *
     * @return {@code false} si la file est pleine — l'appelant doit alors se débarrasser de cet
     *     abonné, jamais réessayer
     */
    boolean offer(PublishedEvent event) {
        return !closed.get() && pending.offerLast(event);
    }

    /**
     * L'instantané, ou la relecture, en tête de file et dans l'ordre.
     *
     * @return {@code false} si tout n'a pas tenu : l'abonné est alors inutilisable, car il
     *     recevrait un état de départ amputé sans jamais le savoir
     */
    boolean offerBacklog(List<PublishedEvent> backlog) {
        for (PublishedEvent event : backlog.reversed()) {
            if (!pending.offerFirst(event)) {
                return false;
            }
        }
        return true;
    }

    /** À n'appeler qu'une fois l'instantané en place : avant, l'ordre n'est pas encore établi. */
    void startPumping() {
        pump.start();
    }

    /** Ferme la connexion de notre propre initiative : arrêt du pod, ou client trop lent. */
    void complete() {
        if (closed.compareAndSet(false, true)) {
            pump.interrupt();
            emitter.complete();
        }
    }

    /**
     * Arrête le pompage sans toucher à l'émetteur.
     *
     * <p>C'est la voie à prendre quand la fermeture vient de l'émetteur lui-même — expiration,
     * client parti, erreur d'écriture : il s'est déjà refermé, et le rappeler à la vie le temps
     * de le refermer encore une fois n'ajoute qu'une exception dans les journaux.
     */
    void detach() {
        if (closed.compareAndSet(false, true)) {
            pump.interrupt();
        }
    }

    boolean isClosed() {
        return closed.get();
    }

    private void drainUntilClosed() {
        try {
            while (!closed.get()) {
                emitter.send(asSseEvent(pending.take()));
            }
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException disconnected) {
            // Le client est parti — onglet fermé, réseau coupé, proxy qui tranche. C'est le
            // cours normal des choses en SSE, pas un incident.
            //
            // Le message seul en DEBUG, la trace en TRACE : une pile de quarante lignes à chaque
            // fermeture d'onglet noie le journal de développement, où `com.runtrack` est en DEBUG.
            // Qui a besoin de la trace la demande, elle n'est pas perdue.
            LOG.debug("Abonné déconnecté pendant l'envoi : {}", rootCauseOf(disconnected));
            LOG.trace("Détail de la déconnexion", disconnected);
            closed.set(true);
            // On termine, on ne signale pas d'erreur. `completeWithError` renvoie l'exception au
            // conteneur, qui la traite comme une requête ratée : le DispatcherServlet la
            // journalise en ERROR, puis tente de rendre `/error` dans une connexion déjà morte —
            // trois piles pour un onglet fermé. Ce n'est visible que derrière un vrai serveur,
            // ce qui est exactement ce que les tests de flux exercent depuis {@code SseStream}.
            emitter.complete();
        }
    }

    /** « Broken pipe » plutôt que trois « caused by » emboîtés qui disent la même chose. */
    private static String rootCauseOf(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private static SseEmitter.SseEventBuilder asSseEvent(PublishedEvent event) {
        var builder = SseEmitter.event().name(event.kind()).data(event.payload());
        // Pas d'« id: » sur un événement d'instantané : le client ne doit pas tenter de
        // reprendre depuis un identifiant que le journal ne connaît pas.
        return event.eventId() == null ? builder : builder.id(event.eventId());
    }
}
