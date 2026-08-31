package com.runtrack.course.internal.infra.realtime;

import com.runtrack.shared.id.ActivityId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;
import org.springframework.stereotype.Component;

/**
 * L'abonnement de cette instance aux courses qu'elle diffuse.
 *
 * <p>Un abonnement par course <em>regardée ici</em>, et non par course en cours : une instance
 * ne consomme que ce qu'elle a quelqu'un à qui donner. C'est ce qui borne le coût — chaque
 * abonnement occupe une connexion Lettuce le temps de son {@code XREAD} bloquant, et une
 * instance sans spectateur n'en ouvre aucune.
 *
 * <p>Le plafond, si on l'atteignait un jour, se lèverait en passant à un stream unique routé en
 * mémoire, pas en ouvrant plus de connexions.
 */
@Component
class RedisLiveStreamWatcher implements LiveStreamWatcher {

    private static final Logger LOG = LoggerFactory.getLogger(RedisLiveStreamWatcher.class);

    /** Le début d'un stream : c'est aussi ce qu'on lit d'un journal encore vide. */
    private static final String FROM_THE_START = "0-0";

    private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
    private final StringRedisTemplate redis;
    private final Map<ActivityId, Subscription> subscriptions = new ConcurrentHashMap<>();

    RedisLiveStreamWatcher(
            StreamMessageListenerContainer<String, MapRecord<String, String, String>> container,
            StringRedisTemplate redis) {
        this.container = container;
        this.redis = redis;
    }

    @Override
    public void watch(ActivityId activityId, Consumer<RecordedEvent> sink) {
        subscriptions.computeIfAbsent(activityId, id -> {
            String key = LiveKeys.events(id);
            return container.receive(
                    StreamOffset.create(key, ReadOffset.from(lastEntryIdOf(key))),
                    entry -> forward(entry, sink));
        });
    }

    /**
     * L'entrée la plus récente au moment où l'on s'abonne — et non {@code $}.
     *
     * <p>La différence n'est pas cosmétique. Avec {@code $}, Spring repart de « maintenant » à
     * <em>chaque</em> lecture : tout ce qui est écrit entre deux {@code XREAD} tombe dans le
     * vide. Un lot de quatre points n'en fait alors parvenir qu'un, et les trois autres
     * disparaissent sans la moindre erreur. Partir d'un identifiant concret bascule la stratégie
     * sur « la suite du dernier lu », qui est la seule qui ne perde rien.
     *
     * <p>Ce qui précède cet identifiant n'est pas rejoué ici : l'instantané et la relecture du
     * {@code Last-Event-ID} s'en chargent, chacun pour le spectateur qui les demande.
     */
    private String lastEntryIdOf(String key) {
        List<MapRecord<String, Object, Object>> newest = redis.opsForStream()
                .reverseRange(key, Range.unbounded(), Limit.limit().count(1));
        return newest == null || newest.isEmpty()
                ? FROM_THE_START
                : newest.getFirst().getId().getValue();
    }

    @Override
    public void unwatch(ActivityId activityId) {
        Subscription subscription = subscriptions.remove(activityId);
        if (subscription != null) {
            container.remove(subscription);
        }
    }

    private static void forward(MapRecord<String, String, String> entry, Consumer<RecordedEvent> sink) {
        RecordedEvent event = LiveEventCodec.decode(entry.getId().getValue(), entry.getValue());
        if (event == null) {
            LOG.debug("Entrée de stream illisible ignorée : {}", entry.getId());
            return;
        }
        sink.accept(event);
    }
}
