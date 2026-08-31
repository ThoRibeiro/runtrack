package com.runtrack.platform.realtime;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Le direct, vu des modules : on publie sur un sujet, on s'y abonne en SSE.
 *
 * <p>Un « sujet » est la clé du Stream Dragonfly qui le porte — {@code live:activity:{id}:events}
 * pour une course, {@code live:user:{id}:notifications} pour une boîte de réception. Chaque module
 * nomme les siens ; cette couche ne fait que transporter.
 */
@Component
public class LiveChannel {

    private static final Logger LOG = LoggerFactory.getLogger(LiveChannel.class);

    private final SseRegistry registry;
    private final StreamLog log;
    private final StringRedisTemplate redis;
    private final RealtimeProperties properties;
    private final Counter published;
    private final Counter failed;

    LiveChannel(SseRegistry registry, StreamLog log, StringRedisTemplate redis,
            RealtimeProperties properties, MeterRegistry meters) {
        this.registry = registry;
        this.log = log;
        this.redis = redis;
        this.properties = properties;
        this.published = Counter.builder("runtrack.live.events.published").register(meters);
        this.failed = Counter.builder("runtrack.live.events.failed").register(meters);
    }

    /**
     * Diffuse, une fois la transaction courante validée.
     *
     * <p><b>Après le commit, jamais avant.</b> Publier depuis l'intérieur de la transaction
     * annoncerait aux abonnés un fait qui peut encore disparaître si elle est annulée — et le
     * conflit optimiste du §4 rend cette annulation ordinaire, pas exceptionnelle. Le report a une
     * seconde vertu : la latence de Dragonfly sort du temps de transaction, donc du temps pendant
     * lequel les lignes touchées restent verrouillées.
     *
     * <p><b>Aucune panne du direct ne remonte à l'appelant.</b> Les faits sont en base ; que
     * personne ne les regarde en temps réel est un moindre mal. L'inverse — perdre un
     * enregistrement parce que le cache est tombé — n'en serait pas un.
     */
    public void publish(String topic, List<PublishedEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        afterCommit(() -> append(topic, events));
    }

    /**
     * Le sujet est clos : plus rien n'y sera publié.
     *
     * <p>L'historique n'est pas effacé sur-le-champ. Un abonné en cours de reconnexion tient un
     * {@code Last-Event-ID} qu'il va vouloir rejouer, et un client mobile qui repasse du métro au
     * réseau met quelques secondes : couper immédiatement lui ferait manquer la fin de ce qu'il
     * regardait. Les clés expirent donc peu après.
     */
    public void close(String topic) {
        afterCommit(() -> expire(topic));
    }

    /** Un abonné sur un sujet vivant : instantané puis direct, jusqu'à ce qu'il parte. */
    public SseEmitter subscribe(String topic, Optional<String> lastEventId,
            Supplier<List<PublishedEvent>> snapshot) {

        var emitter = new SseEmitter(properties.emitterTimeout().toMillis());
        SseSubscriber subscriber = SseSubscriber.attachedTo(emitter, properties.subscriberQueueCapacity());

        // L'ordre qui suit n'est pas interchangeable : on s'abonne d'abord, de sorte que tout ce
        // qui survient à partir de maintenant soit capturé ; on lit l'instantané ensuite ; on
        // l'insère en tête, devant ce qui s'est déjà glissé en file. Inverser laisserait un trou
        // des quelques millisecondes de la lecture, sans que personne ne s'en aperçoive jamais.
        registry.register(topic, subscriber);
        emitter.onCompletion(() -> release(topic, subscriber));
        emitter.onTimeout(() -> release(topic, subscriber));
        emitter.onError(failure -> release(topic, subscriber));

        if (!subscriber.offerBacklog(backlogFor(topic, lastEventId, snapshot))) {
            // L'instantané n'a pas tenu : cet abonné commencerait sur un état amputé sans jamais
            // le savoir. Mieux vaut le renvoyer se reconnecter.
            LOG.warn("Instantané trop volumineux pour la file de l'abonné, sujet {}", topic);
            registry.deregister(topic, subscriber);
            subscriber.complete();
            return emitter;
        }
        subscriber.startPumping();
        return emitter;
    }

    /**
     * Un sujet clos : l'instantané, puis on raccroche.
     *
     * <p>Rien ne sera plus publié. Garder la connexion ouverte occuperait un abonnement Dragonfly
     * et une file pour ne jamais rien y mettre, et laisserait le client attendre un direct qui
     * n'existe plus.
     */
    public SseEmitter sendOnce(Supplier<List<PublishedEvent>> snapshot) {
        var emitter = new SseEmitter(properties.emitterTimeout().toMillis());
        try {
            for (PublishedEvent event : snapshot.get()) {
                emitter.send(SseEmitter.event().name(event.kind()).data(event.payload()));
            }
            emitter.complete();
        } catch (Exception disconnected) {
            emitter.completeWithError(disconnected);
        }
        return emitter;
    }

    private void append(String topic, List<PublishedEvent> events) {
        // MAXLEN approximatif : Dragonfly tronque alors au nœud près plutôt qu'à l'entrée près,
        // ce qui rend l'écriture à coût constant. La borne exacte n'a aucune importance ici —
        // c'est un garde-fou mémoire, pas un quota.
        XAddOptions bounded = XAddOptions.maxlen(properties.streamMaxLength()).approximateTrimming(true);
        try {
            for (PublishedEvent event : events) {
                redis.opsForStream().add(
                        StreamRecords.mapBacked(event.asEntry()).withStreamKey(topic), bounded);
            }
            published.increment(events.size());
        } catch (RuntimeException degraded) {
            failed.increment(events.size());
            LOG.warn("Direct indisponible sur {} : {}", topic, degraded.getMessage());
        }
    }

    private void expire(String topic) {
        try {
            redis.expire(topic, properties.streamRetention());
        } catch (RuntimeException degraded) {
            // Sans ce TTL, la clé s'effacera d'elle-même par la troncature MAXLEN au prochain
            // usage — ou jamais, si le sujet ne redémarre pas. Le pire cas est une clé oubliée.
            LOG.warn("Fermeture impossible sur {} : {}", topic, degraded.getMessage());
        }
    }

    /**
     * Ce que l'abonné reçoit avant le direct : la relecture s'il peut reprendre, l'instantané
     * sinon. Jamais les deux — ce serait le même état deux fois.
     */
    private List<PublishedEvent> backlogFor(String topic, Optional<String> lastEventId,
            Supplier<List<PublishedEvent>> snapshot) {

        return lastEventId
                .flatMap(eventId -> log.replayAfter(topic, eventId))
                .orElseGet(snapshot);
    }

    /** L'émetteur s'est refermé : on range l'abonné sans essayer de le refermer encore. */
    private void release(String topic, SseSubscriber subscriber) {
        subscriber.detach();
        registry.deregister(topic, subscriber);
    }

    /**
     * Diffère l'écriture jusqu'au commit, ou l'exécute tout de suite hors transaction.
     *
     * <p>Le second cas n'est pas théorique : un cycle de vie publie depuis une méthode
     * transactionnelle, mais un écouteur d'événement, lui, s'exécute déjà après le commit.
     */
    private static void afterCommit(Runnable write) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            write.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                write.run();
            }
        });
    }
}
