package com.runtrack.notification.infrastructure.push;

import com.runtrack.notification.usecases.port.PushThrottle;
import com.runtrack.notification.usecases.model.inbox.NotificationType;
import com.runtrack.shared.id.UserId;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Le garde-fou anti-spam, sur une clé qui expire toute seule.
 *
 * <p>{@code SET ... NX EX} en une seule commande : c'est l'atomicité qui fait le garde-fou. Avec
 * un {@code GET} suivi d'un {@code SET}, deux fan-outs simultanés liraient tous deux « rien » et
 * enverraient tous deux le push — précisément le cas qu'on veut empêcher, puisqu'il arrive quand
 * ça s'emballe.
 *
 * <p>Dragonfly plutôt que la base : la donnée expire d'elle-même, personne ne la relit jamais, et
 * sa perte ne coûte qu'un push de trop.
 */
@Component
class RedisPushThrottle implements PushThrottle {

    private static final Logger LOG = LoggerFactory.getLogger(RedisPushThrottle.class);

    private final StringRedisTemplate redis;

    RedisPushThrottle(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean allow(UserId actorId, UserId recipientId, NotificationType type, Duration window) {
        String key = "push:sent:" + type.name() + ":" + actorId + ":" + recipientId;
        try {
            return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, "1", window));
        } catch (RuntimeException degraded) {
            // Dragonfly injoignable : on laisse passer. Un push en double vaut mieux qu'un
            // destinataire qui ne reçoit plus rien parce qu'un cache est tombé.
            LOG.warn("Garde-fou anti-spam indisponible, push laissé passer : {}", degraded.getMessage());
            return true;
        }
    }
}
