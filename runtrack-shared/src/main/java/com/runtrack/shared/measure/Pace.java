package com.runtrack.shared.measure;

import java.time.Duration;
import java.util.Optional;

/** Une allure : le temps mis pour parcourir un kilomètre. */
public record Pace(Duration perKilometer) implements Comparable<Pace> {

    public Pace {
        if (perKilometer == null) {
            throw new IllegalArgumentException("Allure sans durée");
        }
        if (perKilometer.isNegative() || perKilometer.isZero()) {
            throw new IllegalArgumentException("Allure nulle ou négative : " + perKilometer);
        }
    }

    /**
     * L'allure d'un segment, ou {@link Optional#empty()} si elle n'a pas de sens :
     * distance nulle (à l'arrêt) ou durée nulle. Retourner une valeur bidon dans ces cas
     * pollue toutes les moyennes en aval.
     */
    public static Optional<Pace> of(Distance distance, Duration elapsed) {
        if (distance.isZero() || elapsed.isNegative() || elapsed.isZero()) {
            return Optional.empty();
        }
        double secondsPerKilometer = elapsed.toNanos() / (double) Duration.ofSeconds(1).toNanos()
                / distance.toKilometers();
        return Optional.of(new Pace(Duration.ofNanos(Math.round(secondsPerKilometer * 1_000_000_000d))));
    }

    /** La vitesse correspondante, en mètres par seconde. */
    public double metersPerSecond() {
        return Distance.METERS_PER_KILOMETER / (perKilometer.toNanos() / 1_000_000_000d);
    }

    /** Une allure plus rapide est un temps au kilomètre plus court. */
    @Override
    public int compareTo(Pace other) {
        return perKilometer.compareTo(other.perKilometer);
    }
}
