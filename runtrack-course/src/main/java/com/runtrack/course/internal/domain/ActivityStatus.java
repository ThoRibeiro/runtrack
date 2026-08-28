package com.runtrack.course.internal.domain;

import java.time.Instant;

/**
 * L'état d'une course : {@code Live} → {@code Paused} → {@code Live} →
 * {@code Finished} | {@code Discarded}. Les deux derniers sont terminaux.
 *
 * <p>Scellé, donc le compilateur signale tout {@code switch} qui oublie un cas le jour où
 * un état s'ajoute.
 */
public sealed interface ActivityStatus {

    /** L'instant de la dernière transition vers cet état. */
    Instant since();

    /** L'enregistrement est en cours : c'est le seul état qui accepte des points. */
    record Live(Instant since) implements ActivityStatus {
    }

    /** Le coureur a mis en pause ; le temps continue de s'écouler, pas la course. */
    record Paused(Instant since) implements ActivityStatus {
    }

    /** Course terminée : statistiques figées, plus aucune modification d'état. */
    record Finished(Instant since) implements ActivityStatus {
    }

    /** Course abandonnée par le coureur : conservée, mais hors de tout affichage. */
    record Discarded(Instant since) implements ActivityStatus {
    }

    default boolean isTerminal() {
        return this instanceof Finished || this instanceof Discarded;
    }

    default boolean acceptsPoints() {
        return this instanceof Live;
    }
}
