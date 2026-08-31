package com.runtrack.platform.events;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;

/**
 * Les deux règles que Modulith laisse à notre charge : attendre de plus en plus longtemps entre
 * deux tentatives, et cesser d'insister.
 */
class EventPublicationRetryTest {

    private static final Instant NOW = Instant.parse("2026-08-31T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    /** Retient le filtre que la reprise lui passe : c'est lui qui porte toute la décision. */
    private static final class CapturingRegistry implements IncompleteEventPublications {

        private final List<Predicate<EventPublication>> filters = new ArrayList<>();

        @Override
        public void resubmitIncompletePublications(Predicate<EventPublication> filter) {
            filters.add(filter);
        }

        @Override
        public void resubmitIncompletePublicationsOlderThan(Duration duration) {
        }

        @Override
        public void resubmitIncompletePublications(ResubmissionOptions options) {
            filters.add(options.getFilter());
        }

        Predicate<EventPublication> lastFilter() {
            return filters.getLast();
        }
    }

    /** Une publication réduite à ce dont la décision dépend : son âge et ses tentatives. */
    private record FakePublication(int attempts, Instant lastResubmission) implements EventPublication {

        @Override
        public UUID getIdentifier() {
            return UUID.nameUUIDFromBytes(("p" + attempts).getBytes());
        }

        @Override
        public Object getEvent() {
            return "event";
        }

        @Override
        public Instant getPublicationDate() {
            return lastResubmission;
        }

        @Override
        public java.util.Optional<Instant> getCompletionDate() {
            return java.util.Optional.empty();
        }

        @Override
        public Status getStatus() {
            return Status.PUBLISHED;
        }

        @Override
        public Instant getLastResubmissionDate() {
            return lastResubmission;
        }

        @Override
        public int getCompletionAttempts() {
            return attempts;
        }
    }

    /** Un fournisseur qui a le bean, ou qui ne l'a pas — c'est tout ce dont la reprise dépend. */
    private record Provider(IncompleteEventPublications registry)
            implements ObjectProvider<IncompleteEventPublications> {

        @Override
        public IncompleteEventPublications getObject() {
            return registry;
        }

        @Override
        public IncompleteEventPublications getObject(Object... arguments) {
            return registry;
        }

        @Override
        public IncompleteEventPublications getIfAvailable() {
            return registry;
        }

        @Override
        public IncompleteEventPublications getIfUnique() {
            return registry;
        }
    }

    private Predicate<EventPublication> filterAfterOneRun(CapturingRegistry registry) {
        new EventPublicationRetry(new Provider(registry), new EventPublications(null),
                CLOCK, new SimpleMeterRegistry())
                .resubmitWhatIsDue();
        return registry.lastFilter();
    }

    @Test
    void theBackoffDoublesWithEveryFailedAttempt() {
        assertThat(EventPublicationRetry.backoffAfter(0)).isEqualTo(Duration.ofMinutes(1));
        assertThat(EventPublicationRetry.backoffAfter(1)).isEqualTo(Duration.ofMinutes(2));
        assertThat(EventPublicationRetry.backoffAfter(3)).isEqualTo(Duration.ofMinutes(8));
    }

    /** Une publication qui vient d'échouer n'est pas remise en file dans la seconde. */
    @Test
    void aFreshFailureIsLeftAloneUntilItsBackoffHasElapsed() {
        var registry = new CapturingRegistry();
        Predicate<EventPublication> due = filterAfterOneRun(registry);

        assertThat(due.test(new FakePublication(1, NOW.minusSeconds(30)))).isFalse();
        assertThat(due.test(new FakePublication(1, NOW.minus(Duration.ofMinutes(3))))).isTrue();
    }

    /** Le recul se mesure depuis la dernière tentative, sinon l'exponentielle ne sert à rien. */
    @Test
    void anOlderFailureWaitsLongerThanARecentOne() {
        var registry = new CapturingRegistry();
        Predicate<EventPublication> due = filterAfterOneRun(registry);
        Instant fiveMinutesAgo = NOW.minus(Duration.ofMinutes(5));

        assertThat(due.test(new FakePublication(1, fiveMinutesAgo))).isTrue();
        assertThat(due.test(new FakePublication(4, fiveMinutesAgo))).isFalse();
    }

    /** Au-delà du plafond, c'est une lettre morte : on ne réessaie plus, jamais. */
    @Test
    void aPublicationThatHasFailedTooOftenIsNeverRetriedAgain() {
        var registry = new CapturingRegistry();
        Predicate<EventPublication> due = filterAfterOneRun(registry);
        Instant longAgo = NOW.minus(Duration.ofDays(7));

        assertThat(due.test(new FakePublication(EventPublicationRetry.MAX_ATTEMPTS, longAgo))).isFalse();
        assertThat(due.test(new FakePublication(EventPublicationRetry.MAX_ATTEMPTS + 3, longAgo))).isFalse();
    }

    /** Sans registre — contexte de test réduit — la reprise se tait au lieu d'échouer. */
    @Test
    void anAbsentRegistryDisablesTheRetryWithoutFailing() {
        var retry = new EventPublicationRetry(
                new Provider(null), new EventPublications(null), CLOCK, new SimpleMeterRegistry());

        retry.resubmitWhatIsDue();
    }
}
