package com.runtrack.platform.realtime;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Les abonnés connectés <em>à cette instance</em>, par sujet.
 *
 * <p>C'est la moitié locale du fan-out : Dragonfly transporte les événements entre instances,
 * ce registre les distribue à l'intérieur de celle-ci.
 *
 * <p>Il tient aussi l'invariant qui décide de l'abonnement Dragonfly : une instance ne suit un
 * sujet que tant qu'elle a au moins un abonné pour lui. Cet enchaînement — premier arrivé, on
 * s'abonne ; dernier parti, on se désabonne — se fait sous {@link ConcurrentHashMap#compute},
 * donc sous le verrou de la clé. Deux abonnés qui arrivent en même temps ne peuvent pas ouvrir
 * deux abonnements, et un départ ne peut pas fermer celui d'une arrivée simultanée.
 */
@Component
public class SseRegistry implements SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(SseRegistry.class);

    private final Map<String, Set<SseSubscriber>> subscribers = new ConcurrentHashMap<>();
    private final StreamWatcher watcher;
    private final Clock clock;
    private final Counter dropped;
    private volatile boolean running = true;

    SseRegistry(StreamWatcher watcher, Clock clock, MeterRegistry meters) {
        this.watcher = watcher;
        this.clock = clock;
        this.dropped = Counter.builder("runtrack.live.subscribers.dropped")
                .description("Abonnés déconnectés parce qu'ils ne suivaient plus le débit")
                .register(meters);
        Gauge.builder("runtrack.live.subscribers", this, SseRegistry::count)
                .description("Abonnés SSE connectés à cette instance")
                .register(meters);
        Gauge.builder("runtrack.live.topics.watched", this, registry -> registry.subscribers.size())
                .description("Sujets suivis sur le Stream Dragonfly par cette instance")
                .register(meters);
    }

    void register(String topic, SseSubscriber subscriber) {
        subscribers.compute(topic, (id, current) -> {
            Set<SseSubscriber> watching = current == null ? ConcurrentHashMap.newKeySet() : current;
            if (watching.isEmpty()) {
                watcher.watch(id, event -> broadcast(id, event));
            }
            watching.add(subscriber);
            return watching;
        });
    }

    void deregister(String topic, SseSubscriber subscriber) {
        subscribers.compute(topic, (id, current) -> {
            if (current == null) {
                return null;
            }
            current.remove(subscriber);
            if (!current.isEmpty()) {
                return current;
            }
            watcher.unwatch(id);
            // Rendre null retire la clé : garder un ensemble vide par course déjà terminée
            // ferait grossir la carte pour la durée de vie du pod.
            return null;
        });
    }

    /** Distribue un événement, et se débarrasse au passage de ceux qui ne suivent plus. */
    void broadcast(String topic, PublishedEvent event) {
        Set<SseSubscriber> watching = subscribers.get(topic);
        if (watching == null) {
            return;
        }
        // Les retardataires sont collectés puis traités après le parcours : les retirer pendant
        // reviendrait à modifier la carte au milieu d'une itération sur elle.
        var overwhelmed = new ArrayList<SseSubscriber>();
        for (SseSubscriber subscriber : watching) {
            if (!subscriber.offer(event)) {
                overwhelmed.add(subscriber);
            }
        }
        overwhelmed.forEach(subscriber -> disconnectSlow(topic, subscriber));
    }

    /**
     * Le battement de cœur du §4.
     *
     * <p>Sans lui, un proxy ou un équilibreur ferme une connexion SSE restée silencieuse une
     * poignée de minutes — ce qui arrive dès qu'un coureur s'arrête à un feu, ou simplement dès
     * qu'une boîte de réception ne bouge pas.
     */
    @Scheduled(fixedRateString = "${runtrack.realtime.heartbeat:15s}")
    public void heartbeat() {
        PublishedEvent beat = PublishedEvent.withoutId("heartbeat", "\"" + clock.instant() + "\"");
        List.copyOf(subscribers.keySet()).forEach(topic -> broadcast(topic, beat));
    }

    int count() {
        return subscribers.values().stream().mapToInt(Set::size).sum();
    }

    private void disconnectSlow(String topic, SseSubscriber subscriber) {
        dropped.increment();
        LOG.info("Abonné trop lent déconnecté du sujet {}", topic);
        subscriber.complete();
        deregister(topic, subscriber);
    }

    @Override
    public void start() {
        running = true;
    }

    /**
     * L'arrêt gracieux du pod : on ferme proprement, on n'attend pas la coupure.
     *
     * <p>Un émetteur terminé fait rouvrir au client une connexion — qui atterrira sur une autre
     * instance, avec son {@code Last-Event-ID}. Laisser le pod mourir avec ses sockets ouvertes
     * donne le même résultat plusieurs dizaines de secondes plus tard, et une erreur au client
     * entre-temps.
     */
    @Override
    public void stop() {
        running = false;
        List<String> watched = new ArrayList<>(subscribers.keySet());
        watched.forEach(topic -> {
            Set<SseSubscriber> watching = subscribers.remove(topic);
            if (watching != null) {
                watching.forEach(SseSubscriber::complete);
                watcher.unwatch(topic);
            }
        });
        LOG.info("Direct arrêté : {} sujet(s) libéré(s)", watched.size());
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
