package com.runtrack.course.infrastructure.realtime;

import com.runtrack.course.usecases.model.live.LiveEvent;
import com.runtrack.course.infrastructure.endpoint.ActivityMapper;
import com.runtrack.course.infrastructure.dto.LiveDtos;
import com.runtrack.platform.realtime.PublishedEvent;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * L'événement du domaine vers la forme qui voyage.
 *
 * <p>C'est le seul endroit du module qui sache ce qu'est une position ou une statistique sur le
 * fil : au-delà, {@code platform} ne transporte qu'un nom et une charge utile.
 */
@Component
public class LiveEventCodec {

    private final ObjectMapper json;

    LiveEventCodec(ObjectMapper json) {
        this.json = json;
    }

    public PublishedEvent encode(LiveEvent event) {
        return PublishedEvent.withoutId(event.kind(), json.writeValueAsString(payloadOf(event)));
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
