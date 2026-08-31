package com.runtrack.notification.infrastructure.push;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Le réglage du push. Aucun secret ici : le fichier de compte de service est désigné par un
 * chemin, jamais recopié (§9).
 *
 * <p>Types objets et non primitifs : une propriété absente vaudrait {@code 0} ou {@code false}
 * sans que rien ne le signale — c'est ainsi que le cache s'était retrouvé désactivé au lot 6.
 */
@ConfigurationProperties("runtrack.push")
public record PushProperties(
        String projectId,
        String credentialsLocation,
        String baseUrl,
        Integer batchSize,
        Duration timeout) {

    /** La borne du §7 : un lot de push ne dépasse pas 500 appareils. */
    public static final int MAX_BATCH_SIZE = 500;

    public PushProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://fcm.googleapis.com" : baseUrl;
        batchSize = batchSize == null ? MAX_BATCH_SIZE : Math.clamp(batchSize, 1, MAX_BATCH_SIZE);
        timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
    }
}
