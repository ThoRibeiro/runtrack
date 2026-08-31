package com.runtrack.course.internal.infra.realtime;

import com.runtrack.shared.id.ActivityId;
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
 * Les spectateurs connectés <em>à cette instance</em>, par course.
 *
 * <p>C'est la moitié locale du fan-out : Dragonfly transporte les événements entre instances,
 * ce registre les distribue à l'intérieur de celle-ci.
 *
 * <p>Il tient aussi l'invariant qui décide de l'abonnement Dragonfly : une instance ne suit une
 * course que tant qu'elle a au moins un spectateur pour elle. Cet enchaînement — premier
 * arrivé, on s'abonne ; dernier parti, on se désabonne — se fait sous
 * {@link ConcurrentHashMap#compute}, donc sous le verrou de la clé. Deux spectateurs qui
 * arrivent en même temps ne peuvent pas ouvrir deux abonnements, et un départ ne peut pas
 * fermer celui d'une arrivée simultanée.
 */
@Component
public class LiveEmitterRegistry implements SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(LiveEmitterRegistry.class);

    private final Map<ActivityId, Set<LiveSubscriber>> subscribers = new ConcurrentHashMap<>();
    private final LiveStreamWatcher watcher;
    private final Clock clock;
    private final Counter dropped;
    private volatile boolean running = true;

    LiveEmitterRegistry(LiveStreamWatcher watcher, Clock clock, MeterRegistry meters) {
        this.watcher = watcher;
        this.clock = clock;
        this.dropped = Counter.builder("runtrack.live.subscribers.dropped")
                .description("Spectateurs déconnectés parce qu'ils ne suivaient plus le débit")
                .register(meters);
        Gauge.builder("runtrack.live.subscribers", this, LiveEmitterRegistry::count)
                .description("Spectateurs SSE connectés à cette instance")
                .register(meters);
        Gauge.builder("runtrack.live.activities.watched", this, registry -> registry.subscribers.size())
                .description("Courses suivies sur le Stream Dragonfly par cette instance")
                .register(meters);
    }

    void register(ActivityId activityId, LiveSubscriber subscriber) {
        subscribers.compute(activityId, (id, current) -> {
            Set<LiveSubscriber> watching = current == null ? ConcurrentHashMap.newKeySet() : current;
            if (watching.isEmpty()) {
                watcher.watch(id, event -> broadcast(id, event));
            }
            watching.add(subscriber);
            return watching;
        });
    }

    void deregister(ActivityId activityId, LiveSubscriber subscriber) {
        subscribers.compute(activityId, (id, current) -> {
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
    void broadcast(ActivityId activityId, RecordedEvent event) {
        Set<LiveSubscriber> watching = subscribers.get(activityId);
        if (watching == null) {
            return;
        }
        // Les retardataires sont collectés puis traités après le parcours : les retirer pendant
        // reviendrait à modifier la carte au milieu d'une itération sur elle.
        var overwhelmed = new ArrayList<LiveSubscriber>();
        for (LiveSubscriber subscriber : watching) {
            if (!subscriber.offer(event)) {
                overwhelmed.add(subscriber);
            }
        }
        overwhelmed.forEach(subscriber -> disconnectSlow(activityId, subscriber));
    }

    /**
     * Le battement de cœur du §4.
     *
     * <p>Sans lui, un proxy ou un équilibreur ferme une connexion SSE restée silencieuse une
     * poignée de minutes — ce qui arrive à chaque fois qu'un coureur s'arrête à un feu.
     */
    @Scheduled(fixedRateString = "${runtrack.realtime.heartbeat:15s}")
    public void heartbeat() {
        RecordedEvent beat = RecordedEvent.withoutId("heartbeat", "\"" + clock.instant() + "\"");
        List.copyOf(subscribers.keySet()).forEach(activityId -> broadcast(activityId, beat));
    }

    int count() {
        return subscribers.values().stream().mapToInt(Set::size).sum();
    }

    private void disconnectSlow(ActivityId activityId, LiveSubscriber subscriber) {
        dropped.increment();
        LOG.info("Spectateur trop lent déconnecté sur la course {}", activityId);
        subscriber.complete();
        deregister(activityId, subscriber);
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
        List<ActivityId> watched = new ArrayList<>(subscribers.keySet());
        watched.forEach(activityId -> {
            Set<LiveSubscriber> watching = subscribers.remove(activityId);
            if (watching != null) {
                watching.forEach(LiveSubscriber::complete);
                watcher.unwatch(activityId);
            }
        });
        LOG.info("Direct arrêté : {} course(s) libérée(s)", watched.size());
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
