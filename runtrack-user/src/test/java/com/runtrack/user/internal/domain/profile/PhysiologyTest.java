package com.runtrack.user.internal.domain.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDate;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class PhysiologyTest {

    @Test
    void isUnknownUntilSomethingIsFilledIn() {
        assertThat(Physiology.UNKNOWN.isKnown()).isFalse();
    }

    @Test
    void becomesKnownAsSoonAsOneFieldIsSet() {
        var withWeight = new Physiology(
                Optional.empty(), Optional.empty(), OptionalDouble.of(68), OptionalDouble.empty());
        var withBirthDate = new Physiology(
                Optional.of(LocalDate.of(1998, 4, 12)), Optional.empty(), OptionalDouble.empty(), OptionalDouble.empty());
        var withSex = new Physiology(
                Optional.empty(), Optional.of(BiologicalSex.FEMALE), OptionalDouble.empty(), OptionalDouble.empty());
        var withHeight = new Physiology(
                Optional.empty(), Optional.empty(), OptionalDouble.empty(), OptionalDouble.of(172));

        assertThat(withWeight.isKnown()).isTrue();
        assertThat(withBirthDate.isKnown()).isTrue();
        assertThat(withSex.isKnown()).isTrue();
        assertThat(withHeight.isKnown()).isTrue();
    }

    @Test
    void refusesImpossibleMeasurements() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Physiology(
                Optional.empty(), Optional.empty(), OptionalDouble.of(5), OptionalDouble.empty()))
                .withMessageContaining("Masse");
        assertThatIllegalArgumentException().isThrownBy(() -> new Physiology(
                Optional.empty(), Optional.empty(), OptionalDouble.of(500), OptionalDouble.empty()));
        assertThatIllegalArgumentException().isThrownBy(() -> new Physiology(
                Optional.empty(), Optional.empty(), OptionalDouble.empty(), OptionalDouble.of(10)))
                .withMessageContaining("Taille");
        assertThatIllegalArgumentException().isThrownBy(() -> new Physiology(
                Optional.empty(), Optional.empty(), OptionalDouble.empty(), OptionalDouble.of(300)));
    }

    @Test
    void refusesNullInsteadOfAnEmptyOptional() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Physiology(
                null, Optional.empty(), OptionalDouble.empty(), OptionalDouble.empty()));
        assertThatIllegalArgumentException().isThrownBy(() -> new Physiology(
                Optional.empty(), null, OptionalDouble.empty(), OptionalDouble.empty()));
        assertThatIllegalArgumentException().isThrownBy(() -> new Physiology(
                Optional.empty(), Optional.empty(), null, OptionalDouble.empty()));
        assertThatIllegalArgumentException().isThrownBy(() -> new Physiology(
                Optional.empty(), Optional.empty(), OptionalDouble.empty(), null));
    }

    @Test
    void acceptsMeasurementsAtTheBounds() {
        assertThat(new Physiology(Optional.empty(), Optional.empty(),
                OptionalDouble.of(20), OptionalDouble.of(50))).isNotNull();
        assertThat(new Physiology(Optional.empty(), Optional.empty(),
                OptionalDouble.of(400), OptionalDouble.of(280))).isNotNull();
    }
}
