package com.runtrack.course.internal.infra.realtime;

import com.runtrack.course.internal.domain.live.LiveEvent;
import com.runtrack.course.internal.infra.rest.ActivityMapper;
import com.runtrack.course.internal.infra.rest.dto.LiveDtos;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * L'événement du domaine vers la forme qui voyage.
 *
 * <p>Une seule sérialisation pour deux transports : ce qui est écrit dans le Stream Dragonfly
 * est exactement ce que le client SSE reçoit. L'instance qui relaie un événement n'a donc rien
 * à désérialiser — elle recopie la charge utile — et il n'existe aucun moyen que les deux
 * représentations divergent.
 */
@Component
class LiveEventCodec {

    static final String KIND_FIELD = "kind";
    static final String PAYLOAD_FIELD = "payload";

    private final ObjectMapper json;

    LiveEventCodec(ObjectMapper json) {
        this.json = json;
    }

    Map<String, String> encode(LiveEvent event) {
        return Map.of(KIND_FIELD, event.kind(), PAYLOAD_FIELD, json.writeValueAsString(payloadOf(event)));
    }

    /** Le même événement, mais sans identifiant : il ne vient pas du journal. */
    RecordedEvent encodeForSse(LiveEvent event) {
        return RecordedEvent.withoutId(event.kind(), json.writeValueAsString(payloadOf(event)));
    }

    /**
     * Une entrée du stream vers ce que le SSE émet.
     *
     * <p>Une entrée dont il manque un champ est ignorée plutôt que fatale : elle vient d'une
     * version antérieure du format, et faire tomber la diffusion de toute une course pour une
     * entrée illisible serait disproportionné.
     */
    static RecordedEvent decode(String eventId, Map<?, ?> entry) {
        Object kind = entry.get(KIND_FIELD);
        Object payload = entry.get(PAYLOAD_FIELD);
        if (kind == null || payload == null) {
            return null;
        }
        return new RecordedEvent(eventId, kind.toString(), payload.toString());
    }

    private static Object payloadOf(LiveEvent event) {
        return switch (event) {
            case LiveEvent.Position position -> new LiveDtos.PositionEvent(
                    position.sequenceNumber(),
                    position.position().latitude(),
                    position.position().longitude(),
                    position.elevation().meters(),
                    position.recordedAt(),
                    position.heartRate().isPresent() ? position.heartRate().getAsInt() : null);
            case LiveEvent.Stats stats -> ActivityMapper.toStats(stats.stats());
            case LiveEvent.Status status -> new LiveDtos.StatusEvent(status.status(), status.since());
        };
    }
}
