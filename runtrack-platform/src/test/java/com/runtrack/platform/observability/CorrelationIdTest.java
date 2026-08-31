package com.runtrack.platform.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.runtrack.shared.context.CallContext;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/** Le piège du §12 : une portée qui ne franchit pas un fil doit être rouverte explicitement. */
class CorrelationIdTest {

    @Test
    void resumingReopensTheScopeWithTheGivenIdentifier() {
        var seen = new AtomicReference<Optional<CallContext>>();

        CorrelationId.resume("trace-42", () -> seen.set(CallContext.current()));

        assertThat(seen.get()).get().extracting(CallContext::correlationId).isEqualTo("trace-42");
    }

    /**
     * Un événement sans identifiant en reçoit un.
     *
     * <p>Les événements de {@code social} datent d'avant le §12 et n'en portent pas : mieux vaut
     * des journaux liés entre eux qu'aucune corrélation du tout.
     */
    @Test
    void anEventWithoutAnIdentifierStillGetsAScope() {
        var seen = new AtomicReference<Optional<CallContext>>();

        CorrelationId.resume(null, () -> seen.set(CallContext.current()));

        assertThat(seen.get()).isPresent();
        assertThat(seen.get().orElseThrow().correlationId()).isNotBlank();
    }

    @Test
    void theIdentifierIsAvailableToTheLoggerDuringTheScope() {
        var duringScope = new AtomicReference<String>();

        CorrelationId.resume("trace-7", () -> duringScope.set(MDC.get(CorrelationId.MDC_KEY)));

        assertThat(duringScope.get()).isEqualTo("trace-7");
    }

    /**
     * Et surtout : il ne survit pas à la portée.
     *
     * <p>Un MDC qui fuit sur un fil de pool étiquette les traitements suivants avec l'identifiant
     * du précédent — pire que pas de corrélation, parce qu'on croit la lire.
     */
    @Test
    void nothingLeaksOntoTheThreadAfterwards() {
        CorrelationId.resume("trace-7", () -> { });

        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
        assertThat(CallContext.current()).isEmpty();
    }

    @Test
    void theScopeIsCleanedEvenWhenTheWorkFails() {
        try {
            CorrelationId.resume("trace-9", () -> {
                throw new IllegalStateException("traitement en échec");
            });
        } catch (IllegalStateException expected) {
            // Le nettoyage est dans un finally : c'est ce que ce test vérifie.
        }

        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }
}
