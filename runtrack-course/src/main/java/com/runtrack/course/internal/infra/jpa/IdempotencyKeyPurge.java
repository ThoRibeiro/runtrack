package com.runtrack.course.internal.infra.jpa;

import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fait expirer les clés d'idempotence.
 *
 * <p>Sans elle, la table grossit indéfiniment : un client qui envoie un lot toutes les cinq
 * secondes y écrit douze lignes par minute et par course, et chacune porte une réponse JSON
 * complète. Le TTL de 24 h du §4 couvre très largement le rejeu d'un tampon après coupure
 * réseau, qui est le seul cas d'usage de ces clés.
 *
 * <p>Plusieurs instances purgent en même temps sans se coordonner, et c'est sans conséquence :
 * supprimer une ligne déjà supprimée ne fait rien. Un verrou distribué coûterait plus cher
 * que le travail qu'il éviterait.
 */
@Component
class IdempotencyKeyPurge {

    static final Duration RETENTION = Duration.ofHours(24);

    private static final Logger LOG = LoggerFactory.getLogger(IdempotencyKeyPurge.class);

    private final SpringDataIdempotencyRepository keys;
    private final Clock clock;

    IdempotencyKeyPurge(SpringDataIdempotencyRepository keys, Clock clock) {
        this.keys = keys;
        this.clock = clock;
    }

    /** À la 17e minute, pour ne pas tomber en même temps que tous les autres travaux horaires. */
    @Scheduled(cron = "0 17 * * * *")
    @Transactional
    public void purgeExpiredKeys() {
        int purged = keys.deleteOlderThan(clock.instant().minus(RETENTION));
        if (purged > 0) {
            LOG.info("{} clés d'idempotence expirées supprimées", purged);
        }
    }
}
