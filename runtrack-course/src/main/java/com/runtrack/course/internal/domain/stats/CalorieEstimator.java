package com.runtrack.course.internal.domain.stats;

import com.runtrack.course.internal.domain.activity.ActivityType;
import com.runtrack.shared.measure.Distance;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * L'estimation de la dépense énergétique, en kilocalories.
 *
 * <p>Modèle MET : {@code kcal = MET × masse(kg) × durée(h)}, le MET étant dérivé de la
 * vitesse moyenne par un coefficient propre au type d'activité. C'est une approximation
 * — elle ignore le dénivelé, le vent et l'entraînement du coureur — et elle vaut ce que
 * valent les compteurs de calories des montres du commerce.
 *
 * <p>Sans la masse du coureur, le résultat est <em>absent</em>, jamais inventé : un
 * chiffre par défaut serait indiscernable d'une mesure et fausserait tous les cumuls.
 */
public final class CalorieEstimator {

    private static final double SECONDS_PER_HOUR = 3_600d;

    private CalorieEstimator() {
    }

    public static OptionalInt estimate(
            ActivityType type,
            Distance distance,
            Duration movingTime,
            Optional<RunnerPhysiology> physiology) {

        if (physiology.isEmpty() || movingTime.isZero() || movingTime.isNegative() || distance.isZero()) {
            return OptionalInt.empty();
        }

        double hours = movingTime.toNanos() / 1_000_000_000d / SECONDS_PER_HOUR;
        double speedKilometersPerHour = distance.toKilometers() / hours;
        double met = speedKilometersPerHour * type.metPerKilometerPerHour();

        return OptionalInt.of((int) Math.round(met * physiology.get().weightKilograms() * hours));
    }
}
