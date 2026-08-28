package com.runtrack.user.internal.domain.profile;

import java.time.LocalDate;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Les données corporelles du coureur, toutes facultatives.
 *
 * <p>Elles ne servent qu'à l'estimation de dépense énergétique. Sensibles : jamais dans le
 * résumé public d'un profil, jamais exposées à un tiers, purgées à la suppression du compte.
 */
public record Physiology(
        Optional<LocalDate> birthDate,
        Optional<BiologicalSex> sex,
        OptionalDouble weightKilograms,
        OptionalDouble heightCentimeters) {

    private static final double MIN_WEIGHT = 20;
    private static final double MAX_WEIGHT = 400;
    private static final double MIN_HEIGHT = 50;
    private static final double MAX_HEIGHT = 280;

    public static final Physiology UNKNOWN = new Physiology(
            Optional.empty(), Optional.empty(), OptionalDouble.empty(), OptionalDouble.empty());

    public Physiology {
        if (birthDate == null || sex == null || weightKilograms == null || heightCentimeters == null) {
            throw new IllegalArgumentException("Physiologie : utiliser Optional.empty(), pas null");
        }
        requireInRange(weightKilograms, MIN_WEIGHT, MAX_WEIGHT, "Masse");
        requireInRange(heightCentimeters, MIN_HEIGHT, MAX_HEIGHT, "Taille");
    }

    public boolean isKnown() {
        return birthDate.isPresent() || sex.isPresent()
                || weightKilograms.isPresent() || heightCentimeters.isPresent();
    }

    private static void requireInRange(OptionalDouble value, double min, double max, String label) {
        if (value.isPresent() && (value.getAsDouble() < min || value.getAsDouble() > max)) {
            throw new IllegalArgumentException(label + " hors bornes : " + value.getAsDouble());
        }
    }
}
