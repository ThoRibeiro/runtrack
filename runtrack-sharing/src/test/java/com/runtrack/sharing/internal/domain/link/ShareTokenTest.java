package com.runtrack.sharing.internal.domain.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ShareTokenTest {

    /** 256 bits, comme le §3 : c'est ce qui rend le jeton indevinable sans le hacher lentement. */
    @Test
    void carriesTwoHundredAndFiftySixBitsOfRandomness() {
        String value = ShareToken.generate(new SecureRandom()).value();

        // Base64 sans remplissage : 32 octets tiennent en 43 caractères.
        assertThat(value).hasSize(43);
    }

    @Test
    void twoTokensAreNeverTheSame() {
        var random = new SecureRandom();
        Set<String> drawn = new HashSet<>();

        IntStream.range(0, 1_000).forEach(index -> drawn.add(ShareToken.generate(random).value()));

        assertThat(drawn).hasSize(1_000);
    }

    @Test
    void theDigestIsStableAndDiffersFromTheSecret() {
        var token = new ShareToken("un-jeton");

        assertThat(token.hash()).isEqualTo(new ShareToken("un-jeton").hash());
        assertThat(token.hash()).hasSize(64).isNotEqualTo("un-jeton");
    }

    @Test
    void anEmptyTokenIsRefused() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new ShareToken("  "));
    }
}
