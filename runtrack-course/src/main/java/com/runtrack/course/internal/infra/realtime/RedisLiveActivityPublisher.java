package com.runtrack.course.internal.infra.realtime;

import com.runtrack.course.internal.application.port.LiveActivityPublisher;
import com.runtrack.course.internal.domain.live.LiveEvent;
import com.runtrack.shared.id.ActivityId;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Le fan-out entre instances : chaque événement part dans le Stream Dragonfly de sa course.
 *
 * <p><b>Écrit après le commit, jamais avant.</b> Publier depuis l'intérieur de la transaction
 * annoncerait aux spectateurs une position qui peut encore disparaître si elle est annulée —
 * et le conflit optimiste du §4 rend cette annulation ordinaire, pas exceptionnelle. Le report
 * a une seconde vertu : la latence de Dragonfly sort du temps de transaction, donc du temps
 * pendant lequel la ligne de statistiques est verrouillée.
 *
 * <p><b>Aucune panne du direct ne remonte à l'appelant.</b> Les points sont en base ; que
 * personne ne les regarde en direct est un moindre mal. L'inverse — perdre un enregistrement
 * parce que le cache est tombé — n'en serait pas un.
 *
 * <p><b>Pas de Hash {@code live:activity:{id}:state}.</b> Le §4 en prévoit un, avec l'état et
 * les dernières statistiques. Rien ne le lirait : le seul consommateur possible est
 * l'instantané, et celui-ci a déjà chargé la course en base pour vérifier que le spectateur a
 * le droit de la voir. Une seconde copie de l'état que personne ne consulte est une copie qui
 * dérive en silence.
 */
@Component
class RedisLiveActivityPublisher implements LiveActivityPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(RedisLiveActivityPublisher.class);

    private final StringRedisTemplate redis;
    private final LiveEventCodec codec;
    private final RealtimeProperties properties;
    private final Counter published;
    private final Counter failed;

    RedisLiveActivityPublisher(StringRedisTemplate redis, LiveEventCodec codec,
            RealtimeProperties properties, MeterRegistry meters) {
        this.redis = redis;
        this.codec = codec;
        this.properties = properties;
        this.published = Counter.builder("runtrack.live.events.published").register(meters);
        this.failed = Counter.builder("runtrack.live.events.failed").register(meters);
    }

    @Override
    public void publish(ActivityId activityId, List<LiveEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        afterCommit(() -> append(activityId, events));
    }

    @Override
    public void closeStream(ActivityId activityId) {
        afterCommit(() -> expire(activityId));
    }

    private void append(ActivityId activityId, List<LiveEvent> events) {
        String key = LiveKeys.events(activityId);
        // MAXLEN approximatif : Dragonfly tronque alors au nœud près plutôt qu'à l'entrée près,
        // ce qui rend l'écriture à coût constant. La borne exacte n'a aucune importance ici —
        // c'est un garde-fou mémoire, pas un quota.
        XAddOptions bounded = XAddOptions.maxlen(properties.streamMaxLength()).approximateTrimming(true);
        try {
            for (LiveEvent event : events) {
                redis.opsForStream().add(
                        StreamRecords.mapBacked(codec.encode(event)).withStreamKey(key), bounded);
            }
            published.increment(events.size());
        } catch (RuntimeException degraded) {
            failed.increment(events.size());
            LOG.warn("Direct indisponible sur la course {} : {}", activityId, degraded.getMessage());
        }
    }

    private void expire(ActivityId activityId) {
        try {
            redis.expire(LiveKeys.events(activityId), properties.streamRetention());
        } catch (RuntimeException degraded) {
            // Sans ce TTL, la clé s'effacera d'elle-même par la troncature MAXLEN au prochain
            // usage — ou jamais, si la course ne redémarre pas. Le pire cas est une clé oubliée.
            LOG.warn("Fermeture du direct impossible sur {} : {}", activityId, degraded.getMessage());
        }
    }

    /**
     * Diffère l'écriture jusqu'au commit, ou l'exécute tout de suite hors transaction.
     *
     * <p>Le second cas n'est pas théorique : {@code ActivityLifecycle} publie depuis une méthode
     * transactionnelle, mais un appel de service à service peut très bien ne pas l'être.
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
