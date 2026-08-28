package com.runtrack.shared.context;

import com.runtrack.shared.id.UserId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CallContextTest {

    private static final UserId MARIE = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000001"));

    @Test
    void isAbsentOutsideAnyScope() {
        assertThat(CallContext.current()).isEmpty();
    }

    @Test
    void isVisibleInsideItsScope() {
        CallContext context = CallContext.of("corr-1", MARIE);

        context.runWith(() -> assertThat(CallContext.current()).contains(context));
    }

    /**
     * La limite qui coûte cher si on l'ignore : hors de la portée, le contexte disparaît.
     * Sur un chemin asynchrone, le correlationId doit voyager dans le message.
     */
    @Test
    void disappearsOnceTheScopeIsClosed() {
        CallContext.anonymous("corr-2").runWith(() -> assertThat(CallContext.current()).isPresent());

        assertThat(CallContext.current()).isEmpty();
    }

    @Test
    void returnsTheValueProducedInsideItsScope() throws Exception {
        String result = CallContext.anonymous("corr-3")
                .callWith(() -> CallContext.current().orElseThrow().correlationId());

        assertThat(result).isEqualTo("corr-3");
    }

    @Test
    void letsExceptionsOutOfItsScope() {
        CallContext context = CallContext.anonymous("corr-4");

        assertThatThrownBy(() -> context.callWith(() -> {
            throw new IllegalStateException("boum");
        })).isInstanceOf(IllegalStateException.class).hasMessage("boum");
    }

    @Test
    void anAnonymousContextHasNoUser() {
        assertThat(CallContext.anonymous("corr-5").userId()).isEmpty();
    }

    @Test
    void refusesToBeBuiltWithoutACorrelationId() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CallContext(null, Optional.empty()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CallContext("  ", Optional.empty()));
    }

    @Test
    void refusesANullOptional() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CallContext("corr-6", null))
                .withMessageContaining("Optional.empty()");
    }
}
