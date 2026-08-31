package com.runtrack.course.internal.infra.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;

class RecordedEventTest {

    /** L'absence d'identifiant est un état légitime — l'instantané — pas une donnée manquante. */
    @Test
    void anEventOutsideTheLogHasNoIdentifier() {
        RecordedEvent event = RecordedEvent.withoutId("status", "{}");

        assertThat(event.eventId()).isNull();
        assertThat(event.kind()).isEqualTo("status");
    }

    @Test
    void anEventWithoutKindOrPayloadIsRefused() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new RecordedEvent("1-0", null, "{}"));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new RecordedEvent("1-0", "status", null));
    }
}
