package com.runtrack.course.internal.domain.track;

import com.runtrack.course.internal.domain.activity.ActivityType;
import com.runtrack.shared.measure.Distance;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Le tri des points aberrants, avant toute accumulation.
 *
 * <p>Un GPS de téléphone produit régulièrement des points à cinquante mètres de la
 * position réelle, et parfois un saut franc à l'entrée d'un tunnel. Accumuler ces points
 * gonfle la distance et le dénivelé de plusieurs pour cent, sans qu'aucune erreur ne
 * remonte : c'est un résultat faux, pas un plantage.
 */
public final class TrackPointFilter {

    /** Au-delà, la position n'est plus exploitable pour une trace. */
    public static final double MAX_ACCURACY_METERS = 30;

    /** Tolérance résiduelle après correction de la dérive d'horloge. */
    public static final Duration MAX_FUTURE_DRIFT = Duration.ofSeconds(60);

    private TrackPointFilter() {
    }

    /**
     * Le contexte nécessaire au tri : ce qu'on a déjà accepté, et les bornes temporelles.
     *
     * @param previousAccepted le dernier point retenu, absent au tout premier point
     * @param lastAppliedSequence le curseur d'idempotence, {@code -1} si rien n'a été appliqué
     */
    public record Context(
            Optional<TrackPoint> previousAccepted,
            int lastAppliedSequence,
            Instant startedAt,
            Instant serverNow,
            ActivityType type) {

        public Context {
            if (previousAccepted == null || startedAt == null || serverNow == null || type == null) {
                throw new IllegalArgumentException("Contexte de filtrage incomplet");
            }
        }
    }

    /** La raison du rejet, ou {@link Optional#empty()} si le point est retenu. */
    public static Optional<PointRejection> evaluate(TrackPoint candidate, Context context) {
        if (candidate.sequenceNumber() <= context.lastAppliedSequence()) {
            return Optional.of(PointRejection.DUPLICATE_SEQUENCE);
        }
        if (candidate.accuracyMeters() > MAX_ACCURACY_METERS) {
            return Optional.of(PointRejection.ACCURACY_TOO_LOW);
        }
        if (candidate.recordedAt().isBefore(context.startedAt())) {
            return Optional.of(PointRejection.TIMESTAMP_BEFORE_START);
        }
        if (candidate.recordedAt().isAfter(context.serverNow().plus(MAX_FUTURE_DRIFT))) {
            return Optional.of(PointRejection.TIMESTAMP_IN_FUTURE);
        }
        if (context.previousAccepted().isPresent()
                && exceedsPlausibleSpeed(context.previousAccepted().get(), candidate, context.type())) {
            return Optional.of(PointRejection.IMPLAUSIBLE_SPEED);
        }
        return Optional.empty();
    }

    private static boolean exceedsPlausibleSpeed(TrackPoint previous, TrackPoint candidate, ActivityType type) {
        Duration elapsed = Duration.between(previous.recordedAt(), candidate.recordedAt());
        if (elapsed.isZero() || elapsed.isNegative()) {
            // Deux points au même instant : aucune vitesse n'est calculable, on laisse passer.
            return false;
        }
        Distance travelled = DistanceCalculator.between(previous.position(), candidate.position());
        double seconds = elapsed.toNanos() / 1_000_000_000d;
        return travelled.meters() / seconds > type.maxPlausibleSpeedMetersPerSecond();
    }
}
