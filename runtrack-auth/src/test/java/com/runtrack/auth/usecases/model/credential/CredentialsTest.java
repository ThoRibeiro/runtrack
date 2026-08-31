package com.runtrack.auth.usecases.model.credential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.runtrack.shared.id.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CredentialsTest {

    private static final UserId MARIE = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000001"));
    private static final Instant NOW = Instant.parse("2026-08-29T08:00:00Z");
    private static final PasswordHash HASH = new PasswordHash("$argon2id$v=19$...");

    @Test
    void remembersWhenThePasswordLastChanged() {
        Credentials credentials = Credentials.create(MARIE, HASH, NOW);

        credentials.changePassword(new PasswordHash("$argon2id$v=19$autre"), NOW.plusSeconds(3_600));

        assertThat(credentials.passwordHash().value()).endsWith("autre");
        assertThat(credentials.passwordChangedAt()).isEqualTo(NOW.plusSeconds(3_600));
        assertThat(credentials.userId()).isEqualTo(MARIE);
    }

    @Test
    void refusesToBeBuiltIncomplete() {
        assertThatIllegalArgumentException().isThrownBy(() -> Credentials.create(null, HASH, NOW));
        assertThatIllegalArgumentException().isThrownBy(() -> Credentials.create(MARIE, null, NOW));
        assertThatIllegalArgumentException().isThrownBy(() -> Credentials.create(MARIE, HASH, null));
    }

    @Test
    void rehydratesAPersistedState() {
        Credentials credentials = Credentials.rehydrate(MARIE, HASH, NOW);

        assertThat(credentials.passwordChangedAt()).isEqualTo(NOW);
    }
}
