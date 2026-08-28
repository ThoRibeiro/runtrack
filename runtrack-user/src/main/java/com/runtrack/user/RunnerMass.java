package com.runtrack.user;

/**
 * La masse du coureur, en kilogrammes. Seule donnée physiologique qui franchit la
 * frontière du module, parce que seule l'estimation de dépense énergétique en a besoin.
 */
public record RunnerMass(double kilograms) {

    public RunnerMass {
        if (kilograms <= 0) {
            throw new IllegalArgumentException("Masse invalide : " + kilograms);
        }
    }
}
