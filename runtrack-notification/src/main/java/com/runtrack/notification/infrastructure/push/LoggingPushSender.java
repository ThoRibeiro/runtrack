package com.runtrack.notification.infrastructure.push;

import com.runtrack.notification.usecases.port.PushSender;
import com.runtrack.notification.usecases.model.push.DeviceToken;
import com.runtrack.notification.usecases.model.push.PushMessage;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * L'envoyeur de développement : il écrit ce qu'il aurait envoyé.
 *
 * <p>Exigé par le §7 pour qu'on puisse travailler sur les notifications sans compte Firebase. Il
 * est aussi ce qui rend les tests d'intégration possibles sans réseau sortant.
 *
 * <p>Le choix passe par {@code runtrack.push.provider}, pas par un {@code if} : l'application ne
 * sait jamais quel envoyeur elle utilise.
 */
@Component
@ConditionalOnProperty(name = "runtrack.push.provider", havingValue = "logging", matchIfMissing = true)
class LoggingPushSender implements PushSender {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingPushSender.class);

    @Override
    public Result send(List<DeviceToken> devices, PushMessage message) {
        LOG.info("Push (journal) vers {} appareil(s) : « {} — {} » → {}",
                devices.size(), message.title(), message.body(), message.deepLink());
        // Aucun jeton invalide : un envoyeur qui n'envoie rien ne peut rien invalider, et en
        // inventer ferait purger des appareils parfaitement joignables.
        return new Result(devices.size(), Set.of());
    }
}
