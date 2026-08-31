package com.runtrack.platform.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PublishedEventTest {

    /** L'absence d'identifiant est un état légitime — l'instantané — pas une donnée manquante. */
    @Test
    void anEventOutsideTheLogHasNoIdentifier() {
        PublishedEvent event = PublishedEvent.withoutId("status", "{}");

        assertThat(event.eventId()).isNull();
        assertThat(event.kind()).isEqualTo("status");
    }

    @Test
    void anEventWithoutKindOrPayloadIsRefused() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new PublishedEvent("1-0", null, "{}"));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new PublishedEvent("1-0", "status", null));
    }

    @Test
    void whatIsWrittenToTheStreamIsWhatComesBack() {
        PublishedEvent written = PublishedEvent.withoutId("status", "{\"status\":\"Live\"}");

        PublishedEvent read = PublishedEvent.fromEntry("1700000000000-0", written.asEntry());

        assertThat(read.eventId()).isEqualTo("1700000000000-0");
        assertThat(read.kind()).isEqualTo("status");
        assertThat(read.payload()).isEqualTo(written.payload());
    }

    /** Une entrée d'un format antérieur ne doit pas faire tomber la diffusion de tout un sujet. */
    @Test
    void anIncompleteEntryIsIgnoredRatherThanFatal() {
        assertThat(PublishedEvent.fromEntry("1-0", Map.of("kind", "position"))).isNull();
        assertThat(PublishedEvent.fromEntry("1-0", Map.of())).isNull();
    }
}
