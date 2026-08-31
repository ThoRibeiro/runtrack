package com.runtrack.platform.realtime;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Les bornes du direct. Toutes sont des garde-fous : aucune n'est là pour régler un confort.
 *
 * <p>Les types sont des objets et non des primitives. Un {@code boolean} ou un {@code int}
 * absent de la configuration vaut {@code false} ou {@code 0} sans que rien ne le signale —
 * c'est ainsi que le cache s'est retrouvé désactivé par défaut au lot 6.
 */
@ConfigurationProperties("runtrack.realtime")
public record RealtimeProperties(
        Integer streamMaxLength,
        Duration streamRetention,
        Duration heartbeat,
        Duration emitterTimeout,
        Integer subscriberQueueCapacity,
        Duration pollTimeout) {

    public RealtimeProperties {
        streamMaxLength = streamMaxLength == null ? 1_000 : streamMaxLength;
        streamRetention = streamRetention == null ? Duration.ofMinutes(5) : streamRetention;
        heartbeat = heartbeat == null ? Duration.ofSeconds(15) : heartbeat;
        emitterTimeout = emitterTimeout == null ? Duration.ofHours(4) : emitterTimeout;
        subscriberQueueCapacity = subscriberQueueCapacity == null ? 256 : subscriberQueueCapacity;
        pollTimeout = pollTimeout == null ? Duration.ofSeconds(2) : pollTimeout;
    }
}
