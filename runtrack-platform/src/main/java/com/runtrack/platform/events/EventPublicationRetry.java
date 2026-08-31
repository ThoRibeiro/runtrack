package com.runtrack.platform.events;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * La reprise des publications qui n'ont pas abouti, avec un recul qui double à chaque échec.
 *
 * <p>Modulith persiste l'événement, le rejoue au redémarrage et sait le resoumettre ; il ne
 * décide pas <em>quand</em>. Or réessayer immédiatement et sans fin une notification que
 * Firebase refuse, c'est marteler un service en panne au moment précis où il faut le laisser
 * respirer — et remplir les journaux au point de masquer la cause.
 *
 * <p>Deux règles, donc : un recul exponentiel indexé sur le nombre de tentatives déjà faites,
 * et un arrêt franc au-delà de {@link #MAX_ATTEMPTS}. Ce qui dépasse devient une lettre morte :
 * conservée en base, comptée, visible sur {@code /actuator/eventpublications}, et plus jamais
 * réessayée automatiquement. Un échec reproductible ne se résout pas en insistant.
 */
@Component
public class EventPublicationRetry {

    /** Au-delà, ce n'est plus une panne passagère : c'est un traitement qui ne passera pas. */
    public static final int MAX_ATTEMPTS = 5;

    /** Premier recul ; il double ensuite — 1, 2, 4, 8, 16 minutes. */
    static final Duration FIRST_BACKOFF = Duration.ofMinutes(1);

    private static final Logger LOG = LoggerFactory.getLogger(EventPublicationRetry.class);

    /** Autant de reprises en vol : de quoi rattraper un incident sans rejouer tout un backlog. */
    private static final int BATCH_SIZE = 50;

    private final ObjectProvider<IncompleteEventPublications> incomplete;
    private final EventPublications publications;
    private final Clock clock;

    /**
     * Le registre est demandé en {@link ObjectProvider} et non en dépendance dure.
     *
     * <p>{@code platform} est le noyau technique : il est balayé par les contextes réduits des
     * tests de chaque module, où le registre de publications n'existe pas — c'est l'assemblage qui
     * l'apporte. Exiger le bean ici ferait échouer le démarrage de la moitié des tests
     * d'intégration du projet pour une reprise dont ils n'ont que faire.
     */
    EventPublicationRetry(ObjectProvider<IncompleteEventPublications> incomplete,
            EventPublications publications, Clock clock, MeterRegistry meters) {
        this.incomplete = incomplete;
        this.publications = publications;
        this.clock = clock;
        Gauge.builder("runtrack.events.incomplete", publications, EventPublications::incompleteCount)
                .description("Publications d'événements qui n'ont pas encore abouti")
                .register(meters);
        Gauge.builder("runtrack.events.dead_lettered", publications,
                        registry -> registry.deadLetteredCount(MAX_ATTEMPTS))
                .description("Publications abandonnées après " + MAX_ATTEMPTS + " tentatives")
                .register(meters);
    }

    @Scheduled(fixedDelayString = "${runtrack.events.retry-interval:1m}")
    public void resubmitWhatIsDue() {
        IncompleteEventPublications registry = incomplete.getIfAvailable();
        if (registry == null) {
            // Sans registre, il n'y a rien à reprendre — mais le dire, une fois : un déploiement
            // où l'outbox manquerait doit se voir dans les journaux, pas passer pour normal.
            LOG.warn("Aucun registre de publications : la reprise des événements est inactive");
            return;
        }
        Instant now = clock.instant();
        registry.resubmitIncompletePublications(ResubmissionOptions.defaults()
                .withBatchSize(BATCH_SIZE)
                .withMinAge(FIRST_BACKOFF)
                .withFilter(publication -> isDue(publication, now)));
    }

    /**
     * Cette publication a-t-elle assez attendu ?
     *
     * <p>Le recul se mesure depuis la dernière tentative et non depuis la publication : sans quoi
     * une publication ancienne serait rejouée à chaque passage, et l'exponentielle ne servirait
     * à rien.
     */
    private boolean isDue(EventPublication publication, Instant now) {
        int attempts = publication.getCompletionAttempts();
        if (attempts >= MAX_ATTEMPTS) {
            LOG.debug("Publication {} abandonnée après {} tentatives", publication.getIdentifier(), attempts);
            return false;
        }
        return !now.isBefore(publication.getLastResubmissionDate().plus(backoffAfter(attempts)));
    }

    static Duration backoffAfter(int attempts) {
        return FIRST_BACKOFF.multipliedBy(1L << Math.min(attempts, MAX_ATTEMPTS));
    }
}
