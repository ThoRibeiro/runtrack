package com.runtrack.shared.access;

import static com.runtrack.shared.access.AudienceScope.FOLLOWERS;
import static com.runtrack.shared.access.AudienceScope.PRIVATE;
import static com.runtrack.shared.access.AudienceScope.PUBLIC;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AudienceScopeTest {

    @ParameterizedTest
    @CsvSource({
            "PUBLIC,    PUBLIC,    PUBLIC",
            "PUBLIC,    FOLLOWERS, FOLLOWERS",
            "PUBLIC,    PRIVATE,   PRIVATE",
            "FOLLOWERS, FOLLOWERS, FOLLOWERS",
            "FOLLOWERS, PRIVATE,   PRIVATE",
            "PRIVATE,   PRIVATE,   PRIVATE"})
    void keepsTheMostClosedOfTheTwo(AudienceScope left, AudienceScope right, AudienceScope expected) {
        assertThat(left.mostRestrictive(right)).isEqualTo(expected);
    }

    /**
     * La propriété qui compte : passer la visibilité du compte là où le code attend celle
     * de la course ne peut pas changer le résultat. C'est ce qui rend la confusion des
     * deux niveaux inoffensive.
     */
    @ParameterizedTest
    @CsvSource({"PUBLIC, PRIVATE", "PUBLIC, FOLLOWERS", "FOLLOWERS, PRIVATE", "PRIVATE, PRIVATE"})
    void composesCommutatively(AudienceScope left, AudienceScope right) {
        assertThat(left.mostRestrictive(right)).isEqualTo(right.mostRestrictive(left));
    }

    @Test
    void comparesOpenness() {
        assertThat(PUBLIC.isAtLeastAsOpenAs(FOLLOWERS)).isTrue();
        assertThat(FOLLOWERS.isAtLeastAsOpenAs(FOLLOWERS)).isTrue();
        assertThat(PRIVATE.isAtLeastAsOpenAs(FOLLOWERS)).isFalse();
    }
}
