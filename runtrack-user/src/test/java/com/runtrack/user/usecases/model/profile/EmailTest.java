package com.runtrack.user.usecases.model.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmailTest {

    /** Normalisée, sinon deux comptes indiscernables cohabitent. */
    @Test
    void normalisesCaseAndSurroundingSpace() {
        assertThat(new Email("  Marie.Dupont@Auchan.FR ").value()).isEqualTo("marie.dupont@auchan.fr");
    }

    @Test
    void exposesItsDomain() {
        assertThat(new Email("marie@example.com").domain()).isEqualTo("example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "marie@example.com",
            "marie+course@example.co.uk",
            "o'brien@example.org",
            "m@a.io"})
    void acceptsAddressesThatAStricterPatternWouldReject(String candidate) {
        assertThat(new Email(candidate)).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"marie", "marie@", "@example.com", "marie@example", "ma rie@example.com", "a@b@c.com"})
    void refusesMalformedAddresses(String candidate) {
        assertThatIllegalArgumentException().isThrownBy(() -> new Email(candidate));
    }

    @Test
    void refusesAnAbsentAddress() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Email(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new Email("   "));
    }

    @Test
    void refusesAnOversizedAddress() {
        String tooLong = "a".repeat(250) + "@example.com";

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Email(tooLong))
                .withMessageContaining("trop longue");
    }
}
