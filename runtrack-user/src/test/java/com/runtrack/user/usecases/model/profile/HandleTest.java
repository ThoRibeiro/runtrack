package com.runtrack.user.usecases.model.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HandleTest {

    /** Sans normalisation, « Marie » et « marie » seraient deux comptes que rien ne distingue. */
    @Test
    void normalisesCase() {
        assertThat(new Handle("MarieCourt").value()).isEqualTo("mariecourt");
    }

    @ParameterizedTest
    @ValueSource(strings = {"marie", "marie_court", "marie.court", "marie-court", "m4rie", "abc"})
    void acceptsTheAllowedShapes(String candidate) {
        assertThat(new Handle(candidate)).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"_marie", "marie_", ".marie", "marie.", "-marie", "marie-"})
    void refusesSeparatorsAtTheEdges(String candidate) {
        assertThatIllegalArgumentException().isThrownBy(() -> new Handle(candidate));
    }

    @ParameterizedTest
    @ValueSource(strings = {"marie court", "marie@court", "marié", "marie/court"})
    void refusesCharactersThatWouldBreakAnUrl(String candidate) {
        assertThatIllegalArgumentException().isThrownBy(() -> new Handle(candidate));
    }

    @Test
    void enforcesItsLengthBounds() {
        assertThat(new Handle("a".repeat(Handle.MIN_LENGTH))).isNotNull();
        assertThat(new Handle("a".repeat(Handle.MAX_LENGTH))).isNotNull();

        assertThatIllegalArgumentException().isThrownBy(() -> new Handle("a".repeat(Handle.MIN_LENGTH - 1)));
        assertThatIllegalArgumentException().isThrownBy(() -> new Handle("a".repeat(Handle.MAX_LENGTH + 1)));
    }

    @Test
    void refusesAnAbsentHandle() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Handle(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new Handle("  "));
    }
}
