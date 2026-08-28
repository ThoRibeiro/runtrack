package com.runtrack.shared;

import java.util.Optional;

/**
 * Le contexte d'un appel : de quoi corréler des journaux d'un bout à l'autre d'une
 * requête.
 *
 * <p>Porté par un {@link ScopedValue}, donc valable dans la seule portée dynamique de
 * {@link #runWith}. Il ne franchit ni un {@code @ApplicationModuleListener} (autre thread,
 * après commit), ni un consommateur de flux, ni un envoi push : sur ces chemins-là le
 * {@code correlationId} voyage <em>dans</em> l'événement ou le message, et le contexte se
 * réinstalle à l'entrée du traitement. Compter sur la propagation automatique donne des
 * journaux corrélés sur le seul chemin HTTP, ce qui ne se voit qu'en production.
 */
public record CallContext(String correlationId, Optional<UserId> userId) {

    private static final ScopedValue<CallContext> CURRENT = ScopedValue.newInstance();

    public CallContext {
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("CallContext sans correlationId");
        }
        if (userId == null) {
            throw new IllegalArgumentException("CallContext sans userId : utiliser Optional.empty()");
        }
    }

    public static CallContext anonymous(String correlationId) {
        return new CallContext(correlationId, Optional.empty());
    }

    public static CallContext of(String correlationId, UserId userId) {
        return new CallContext(correlationId, Optional.of(userId));
    }

    /** Le contexte courant, vide hors de toute portée {@link #runWith}. */
    public static Optional<CallContext> current() {
        return CURRENT.isBound() ? Optional.of(CURRENT.get()) : Optional.empty();
    }

    public void runWith(Runnable action) {
        ScopedValue.where(CURRENT, this).run(action);
    }

    public <T, X extends Throwable> T callWith(ScopedValue.CallableOp<? extends T, X> action) throws X {
        return ScopedValue.where(CURRENT, this).call(action);
    }
}
