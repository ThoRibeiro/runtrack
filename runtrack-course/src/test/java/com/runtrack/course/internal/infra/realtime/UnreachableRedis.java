package com.runtrack.course.internal.infra.realtime;

import java.time.Duration;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Un Dragonfly injoignable, sans conteneur.
 *
 * <p>La dégradation gracieuse est une exigence du §6 comme du §4, et c'est précisément le genre
 * de chemin qu'aucun test ne parcourt jamais par hasard : il faut provoquer la panne. Sous-classer
 * le template coûte trois lignes, là où couper un conteneur en coûte dix secondes par test.
 */
final class UnreachableRedis extends StringRedisTemplate {

    static final RuntimeException FAILURE = new IllegalStateException("Dragonfly injoignable");

    @Override
    public <HK, HV> StreamOperations<String, HK, HV> opsForStream() {
        throw FAILURE;
    }

    @Override
    public Boolean expire(String key, Duration timeout) {
        throw FAILURE;
    }
}
