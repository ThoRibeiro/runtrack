package com.runtrack.platform.events;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

/**
 * {@code /actuator/eventpublications} : où en est l'outbox.
 *
 * <p>Trois nombres, et c'est voulu. {@code incomplete} monte et redescend au fil des reprises,
 * c'est normal ; {@code deadLettered} au-dessus de zéro appelle quelqu'un ; et l'âge de la plus
 * vieille en souffrance est ce qui distingue un pic passager d'une file qui n'avance plus.
 */
@Component
@Endpoint(id = "eventpublications")
public class EventPublicationsEndpoint {

    private final EventPublications publications;
    private final Clock clock;

    EventPublicationsEndpoint(EventPublications publications, Clock clock) {
        this.publications = publications;
        this.clock = clock;
    }

    @ReadOperation
    public Map<String, Object> publications() {
        var report = new LinkedHashMap<String, Object>();
        report.put("incomplete", publications.incompleteCount());
        report.put("deadLettered", publications.deadLetteredCount(EventPublicationRetry.MAX_ATTEMPTS));
        report.put("maxAttempts", EventPublicationRetry.MAX_ATTEMPTS);
        publications.oldestIncompleteAge(clock.instant())
                .ifPresent(age -> report.put("oldestIncompleteSeconds", age.toSeconds()));
        return report;
    }
}
