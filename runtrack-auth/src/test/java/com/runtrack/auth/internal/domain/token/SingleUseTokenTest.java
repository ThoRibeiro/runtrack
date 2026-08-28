package com.runtrack.auth.internal.domain.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.runtrack.shared.error.ConflictException;
import com.runtrack.shared.id.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SingleUseTokenTest {

    private static final UserId MARIE = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000001"));
    private static final Instant ISSUED = Instant.parse("2026-08-29T08:00:00Z");

    private static SingleUseToken issued(TokenPurpose purpose) {
        return SingleUseToken.issue(UUID.randomUUID(), MARIE, purpose, "hash", ISSUED);
    }

    /** Un lien de réinitialisation reste une porte d'entrée tant qu'il vit : il vit moins longtemps. */
    @Test
    void lifetimeFollowsThePurpose() {
        assertThat(issued(TokenPurpose.PASSWORD_RESET).expiresAt())
                .isBefore(issued(TokenPurpose.EMAIL_VERIFICATION).expiresAt());
        assertThat(TokenPurpose.PASSWORD_RESET.lifetime()).isEqualTo(java.time.Duration.ofMinutes(30));
        assertThat(TokenPurpose.EMAIL_VERIFICATION.lifetime()).isEqualTo(java.time.Duration.ofDays(1));
    }

    @Test
    void worksOnce() {
        SingleUseToken token = issued(TokenPurpose.EMAIL_VERIFICATION);

        token.consume(ISSUED.plusSeconds(60));

        assertThat(token.consumedAt()).isEqualTo(ISSUED.plusSeconds(60));
        assertThat(token.isUsableAt(ISSUED.plusSeconds(61))).isFalse();
    }

    @Test
    void refusesASecondUse() {
        SingleUseToken token = issued(TokenPurpose.PASSWORD_RESET);
        token.consume(ISSUED.plusSeconds(60));

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> token.consume(ISSUED.plusSeconds(120)))
                .extracting(ConflictException::code)
                .isEqualTo("TOKEN_ALREADY_USED");
    }

    @Test
    void refusesAnExpiredToken() {
        SingleUseToken token = issued(TokenPurpose.PASSWORD_RESET);
        Instant tooLate = ISSUED.plus(TokenPurpose.PASSWORD_RESET.lifetime());

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> token.consume(tooLate))
                .extracting(ConflictException::code)
                .isEqualTo("TOKEN_EXPIRED");
        assertThat(token.isUsableAt(tooLate)).isFalse();
    }

    @Test
    void refusesAnIncompleteToken() {
        assertThatIllegalArgumentException().isThrownBy(() -> SingleUseToken.issue(
                null, MARIE, TokenPurpose.PASSWORD_RESET, "hash", ISSUED));
        assertThatIllegalArgumentException().isThrownBy(() -> SingleUseToken.issue(
                UUID.randomUUID(), null, TokenPurpose.PASSWORD_RESET, "hash", ISSUED));
        assertThatIllegalArgumentException().isThrownBy(() -> SingleUseToken.issue(
                UUID.randomUUID(), MARIE, null, "hash", ISSUED));
    }

    @Test
    void rehydratesAPersistedState() {
        SingleUseToken token = SingleUseToken.rehydrate(
                UUID.randomUUID(), MARIE, TokenPurpose.EMAIL_VERIFICATION, "hash",
                ISSUED.plusSeconds(600), null);

        assertThat(token.purpose()).isEqualTo(TokenPurpose.EMAIL_VERIFICATION);
        assertThat(token.userId()).isEqualTo(MARIE);
        assertThat(token.tokenHash()).isEqualTo("hash");
        assertThat(token.id()).isNotNull();
        assertThat(token.isUsableAt(ISSUED)).isTrue();
    }
}
