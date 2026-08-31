package com.runtrack.platform.realtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * La relecture du journal d'un sujet, pour qu'une reconnexion ne laisse pas de trou.
 *
 * <p>Le client renvoie en {@code Last-Event-ID} le dernier identifiant qu'il a reçu ; on lui
 * rend ce qui vient après. Deux issues, et les distinguer est tout l'intérêt de la classe :
 * <ul>
 *   <li>une liste, éventuellement vide — « voilà ce que tu as manqué, il n'y a pas de trou » ;</li>
 *   <li>rien du tout — « ton identifiant est plus vieux que ce que je conserve, je ne peux pas
 *       te garantir la continuité ». L'appelant retombe alors sur l'instantané.</li>
 * </ul>
 *
 * <p>Cette relecture est du transport, pas du métier : elle ne remonte pas jusqu'à un port. Un
 * cas d'usage sait décrire un état ; savoir ce qu'un socket coupé a manqué ne le regarde pas.
 */
@Component
class StreamLog {

    private static final Logger LOG = LoggerFactory.getLogger(StreamLog.class);

    /** Une reprise qui dépasserait ce volume n'est plus une reprise : l'instantané est plus court. */
    private static final int MAX_REPLAY = 500;

    private final StringRedisTemplate redis;

    StreamLog(StringRedisTemplate redis) {
        this.redis = redis;
    }

    Optional<List<PublishedEvent>> replayAfter(String topic, String lastEventId) {
        try {
            if (isTruncatedBefore(topic, lastEventId)) {
                return Optional.empty();
            }
            List<MapRecord<String, Object, Object>> entries = redis.opsForStream().range(
                    topic,
                    Range.from(Range.Bound.exclusive(lastEventId)).to(Range.Bound.unbounded()),
                    Limit.limit().count(MAX_REPLAY));
            return Optional.of(decode(entries));
        } catch (IllegalArgumentException malformed) {
            // Un Last-Event-ID inventé par un client : on ne devine pas, on reprend au propre.
            LOG.debug("Last-Event-ID illisible sur {} : {}", topic, lastEventId);
            return Optional.empty();
        } catch (RuntimeException degraded) {
            LOG.warn("Relecture impossible sur {} : {}", topic, degraded.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Le journal a-t-il déjà oublié ce que le client attend ?
     *
     * <p>Si la plus ancienne entrée conservée est postérieure à l'identifiant du client, ce qui
     * séparait les deux a été tronqué. Un journal vide compte aussi : il ne prouve rien.
     */
    private boolean isTruncatedBefore(String key, String lastEventId) {
        List<MapRecord<String, Object, Object>> oldest = redis.opsForStream()
                .range(key, Range.unbounded(), Limit.limit().count(1));
        if (oldest == null || oldest.isEmpty()) {
            return true;
        }
        String firstRetained = oldest.getFirst().getId().getValue();
        return StreamEntryId.compare(firstRetained, lastEventId) > 0;
    }

    private static List<PublishedEvent> decode(List<MapRecord<String, Object, Object>> entries) {
        if (entries == null) {
            return List.of();
        }
        var replayed = new ArrayList<PublishedEvent>(entries.size());
        for (MapRecord<String, Object, Object> entry : entries) {
            PublishedEvent event = PublishedEvent.fromEntry(entry.getId().getValue(), entry.getValue());
            if (event != null) {
                replayed.add(event);
            }
        }
        return List.copyOf(replayed);
    }
}
