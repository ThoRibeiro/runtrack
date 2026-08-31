package com.runtrack.auth.usecases.model.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class OpaqueTokenTest {

    private static final RandomGenerator RANDOM = new java.util.Random(7);

    @Test
    void producesASecretAndItsHash() {
        OpaqueToken token = OpaqueToken.generate(RANDOM);

        assertThat(token.secret()).isNotBlank();
        assertThat(token.hash()).isEqualTo(OpaqueToken.hashOf(token.secret()));
    }

    /** Ce que la base contient ne doit jamais permettre de rejouer le jeton. */
    @Test
    void theStoredHashDoesNotRevealTheSecret() {
        OpaqueToken token = OpaqueToken.generate(RANDOM);

        assertThat(token.hash()).doesNotContain(token.secret());
        assertThat(token.hash()).hasSize(64);
    }

    @Test
    void twoDrawsDiffer() {
        assertThat(OpaqueToken.generate(RANDOM).secret())
                .isNotEqualTo(OpaqueToken.generate(RANDOM).secret());
    }

    @Test
    void hashingIsStable() {
        assertThat(OpaqueToken.hashOf("un-secret")).isEqualTo(OpaqueToken.hashOf("un-secret"));
        assertThat(OpaqueToken.hashOf("un-secret")).isNotEqualTo(OpaqueToken.hashOf("un-secret-2"));
    }

    @Test
    void refusesAnIncompleteToken() {
        assertThatIllegalArgumentException().isThrownBy(() -> new OpaqueToken(null, "hash"));
        assertThatIllegalArgumentException().isThrownBy(() -> new OpaqueToken("secret", "  "));
    }
}
