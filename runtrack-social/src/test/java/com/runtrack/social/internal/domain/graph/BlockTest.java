package com.runtrack.social.internal.domain.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.runtrack.shared.error.ConflictException;
import com.runtrack.shared.id.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BlockTest {

    private static final UserId MARIE = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000001"));
    private static final UserId PAUL = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000002"));
    private static final Instant NOW = Instant.parse("2026-08-29T08:00:00Z");

    @Test
    void recordsWhoBlockedWhom() {
        Block block = new Block(UUID.randomUUID(), MARIE, PAUL, NOW);

        assertThat(block.blockerId()).isEqualTo(MARIE);
        assertThat(block.blockedId()).isEqualTo(PAUL);
        assertThat(block.at()).isEqualTo(NOW);
    }

    @Test
    void nobodyBlocksThemselves() {
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> new Block(UUID.randomUUID(), MARIE, MARIE, NOW))
                .extracting(ConflictException::code)
                .isEqualTo("SELF_BLOCK");
    }

    @Test
    void refusesToBeBuiltIncomplete() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Block(null, MARIE, PAUL, NOW));
        assertThatIllegalArgumentException().isThrownBy(() -> new Block(UUID.randomUUID(), null, PAUL, NOW));
        assertThatIllegalArgumentException().isThrownBy(() -> new Block(UUID.randomUUID(), MARIE, null, NOW));
        assertThatIllegalArgumentException().isThrownBy(() -> new Block(UUID.randomUUID(), MARIE, PAUL, null));
    }
}
