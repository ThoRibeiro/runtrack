package com.runtrack.course.internal.infra.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import com.runtrack.api.ApiIntegrationTest;
import com.runtrack.course.internal.infra.jpa.entity.IdempotencyKeyEntity;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * L'expiration des clés d'idempotence, contre la vraie table.
 *
 * <p>Ce test vit dans le paquet du code qu'il exerce, et non dans {@code com.runtrack.api} :
 * la purge et son dépôt sont volontairement de portée paquet, et il n'y a aucune raison de
 * les rendre publics pour la seule commodité d'un test.
 */
class IdempotencyKeyPurgeIT extends ApiIntegrationTest {

    @Autowired
    private SpringDataIdempotencyRepository keys;

    @Autowired
    private IdempotencyKeyPurge purge;

    @Autowired
    private Clock clock;

    private UUID storeKeyAgedBy(long hours) {
        UUID activityId = UUID.randomUUID();
        keys.save(new IdempotencyKeyEntity(
                activityId, "buffer-1", "0".repeat(64), "{}",
                clock.instant().minus(java.time.Duration.ofHours(hours))));
        return activityId;
    }

    @Test
    void purgesWhatHasOutlivedItsRetentionAndKeepsTheRest() {
        UUID expired = storeKeyAgedBy(IdempotencyKeyPurge.RETENTION.toHours() + 1);
        UUID fresh = storeKeyAgedBy(1);

        purge.purgeExpiredKeys();

        assertThat(keys.findById(IdempotencyKeyEntity.idOf(expired, "buffer-1"))).isEmpty();
        assertThat(keys.findById(IdempotencyKeyEntity.idOf(fresh, "buffer-1"))).isPresent();
    }
}
