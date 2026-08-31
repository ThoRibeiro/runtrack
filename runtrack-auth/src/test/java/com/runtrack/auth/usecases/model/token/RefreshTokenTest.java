package com.runtrack.auth.usecases.model.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.runtrack.shared.error.ForbiddenException;
import com.runtrack.shared.id.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    private static final UserId MARIE = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000001"));
    private static final Instant ISSUED = Instant.parse("2026-08-29T08:00:00Z");
    private static final Instant EXPIRES = ISSUED.plusSeconds(30 * 24 * 3_600L);

    private static RefreshToken fresh() {
        return RefreshToken.openFamily(UUID.randomUUID(), MARIE, "hash-1", ISSUED, EXPIRES);
    }

    @Test
    void aLoginOpensANewFamily() {
        RefreshToken first = fresh();
        RefreshToken second = fresh();

        assertThat(first.familyId()).isNotEqualTo(second.familyId());
        assertThat(first.isUsableAt(ISSUED)).isTrue();
        assertThat(first.wasConsumed()).isFalse();
    }

    /** Un rafraîchissement prolonge la chaîne : même famille, même utilisateur. */
    @Test
    void aRefreshStaysInTheSameFamily() {
        RefreshToken first = fresh();
        first.consume(ISSUED.plusSeconds(60));

        RefreshToken next = first.succeededBy(UUID.randomUUID(), "hash-2", ISSUED.plusSeconds(60), EXPIRES);

        assertThat(next.familyId()).isEqualTo(first.familyId());
        assertThat(next.userId()).isEqualTo(MARIE);
        assertThat(next.isUsableAt(ISSUED.plusSeconds(60))).isTrue();
    }

    @Test
    void consumingMarksItUsed() {
        RefreshToken token = fresh();

        token.consume(ISSUED.plusSeconds(60));

        assertThat(token.wasConsumed()).isTrue();
        assertThat(token.consumedAt()).isEqualTo(ISSUED.plusSeconds(60));
        assertThat(token.isUsableAt(ISSUED.plusSeconds(61))).isFalse();
    }

    /**
     * Le signal de vol : un jeton déjà consommé qui se represente. Quelqu'un détient une
     * copie — c'est ce qui déclenche la révocation de toute la famille, côté cas d'usage.
     */
    @Test
    void reuseIsReportedDistinctly() {
        RefreshToken token = fresh();
        token.consume(ISSUED.plusSeconds(60));

        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> token.consume(ISSUED.plusSeconds(120)))
                .extracting(ForbiddenException::code)
                .isEqualTo("REFRESH_TOKEN_REUSED");
    }

    @Test
    void aRevokedTokenIsRefusedBeforeAnythingElse() {
        RefreshToken token = fresh();
        token.revoke();

        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> token.consume(ISSUED.plusSeconds(60)))
                .extracting(ForbiddenException::code)
                .isEqualTo("REFRESH_TOKEN_REVOKED");
        assertThat(token.isRevoked()).isTrue();
        assertThat(token.isUsableAt(ISSUED.plusSeconds(60))).isFalse();
    }

    @Test
    void anExpiredTokenIsRefused() {
        RefreshToken token = fresh();

        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> token.consume(EXPIRES))
                .extracting(ForbiddenException::code)
                .isEqualTo("REFRESH_TOKEN_EXPIRED");
        assertThat(token.isUsableAt(EXPIRES)).isFalse();
    }

    @Test
    void refusesAnIncoherentLifetime() {
        assertThatIllegalArgumentException().isThrownBy(() -> RefreshToken.openFamily(
                UUID.randomUUID(), MARIE, "hash", ISSUED, ISSUED));
        assertThatIllegalArgumentException().isThrownBy(() -> RefreshToken.openFamily(
                UUID.randomUUID(), null, "hash", ISSUED, EXPIRES));
    }

    @Test
    void rehydratesAPersistedState() {
        UUID family = UUID.randomUUID();
        RefreshToken token = RefreshToken.rehydrate(
                UUID.randomUUID(), MARIE, family, "hash", ISSUED, EXPIRES, ISSUED.plusSeconds(10), true);

        assertThat(token.familyId()).isEqualTo(family);
        assertThat(token.wasConsumed()).isTrue();
        assertThat(token.isRevoked()).isTrue();
        assertThat(token.issuedAt()).isEqualTo(ISSUED);
        assertThat(token.expiresAt()).isEqualTo(EXPIRES);
        assertThat(token.tokenHash()).isEqualTo("hash");
        assertThat(token.id()).isNotNull();
    }
}
