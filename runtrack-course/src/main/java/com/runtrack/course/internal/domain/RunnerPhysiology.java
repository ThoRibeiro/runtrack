package com.runtrack.course.internal.domain;

/**
 * Ce que le domaine des courses a besoin de savoir du coureur, et rien de plus : sa masse.
 *
 * <p>Le profil en sait davantage — taille, date de naissance, sexe — mais l'estimation de
 * calories n'en a pas l'usage. Un domaine qui ne réclame que ce qu'il consomme reste
 * testable sans monter la moitié du module {@code user}.
 */
public record RunnerPhysiology(double weightKilograms) {

    public RunnerPhysiology {
        if (Double.isNaN(weightKilograms) || weightKilograms <= 0) {
            throw new IllegalArgumentException("Masse invalide : " + weightKilograms);
        }
    }
}
