package com.runtrack.auth.usecases.model.credential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordTest {

    @ParameterizedTest
    @ValueSource(strings = {"correcthorsebatterystaple", "Un-Mot-De-Passe-Long", "aB3$xY9!qW2z"})
    void acceptsLongEnoughPasswords(String candidate) {
        assertThat(new Password(candidate)).isNotNull();
    }

    @Test
    void enforcesAMinimumLength() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Password("abcdefghijk"))
                .withMessageContaining("au moins");
    }

    /** Une entrée démesurée fait travailler Argon2 pour rien : c'est un vecteur de déni de service. */
    @Test
    void refusesAnAbsurdlyLongPassword() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Password("a".repeat(Password.MAX_LENGTH + 1)))
                .withMessageContaining("dépasse");
    }

    @Test
    void refusesARepetitivePassword() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Password("aaaabbbbaaaa"))
                .withMessageContaining("répétitif");
    }

    @Test
    void refusesAnAbsentPassword() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Password(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new Password(""));
    }

    @Test
    void refusesAnEmptyHash() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PasswordHash(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new PasswordHash("  "));
    }
}
