package com.runtrack.course.internal.domain;

import com.runtrack.shared.Elevation;

/**
 * Le cumul du dénivelé, avec hystérésis.
 *
 * <p>L'altitude d'un GPS oscille de deux à trois mètres à l'arrêt. Additionner
 * naïvement chaque variation positive fait doubler ou tripler le D+ sur une sortie d'une
 * heure : le bruit s'accumule, jamais il ne se compense. On ne retient donc un changement
 * que lorsqu'il s'écarte de plus de {@link #THRESHOLD_METERS} de la dernière altitude de
 * référence, laquelle ne bouge qu'à ce moment-là.
 *
 * <p>Immuable : c'est ce qui permet de le loger dans l'accumulateur de statistiques et de
 * rejouer une course sans effet de bord.
 */
public record ElevationSmoother(Elevation reference, double gain, double loss) {

    /** Sous ce seuil, une variation d'altitude est du bruit de capteur, pas du relief. */
    public static final double THRESHOLD_METERS = 3.0;

    public ElevationSmoother {
        if (reference == null) {
            throw new IllegalArgumentException("ElevationSmoother sans altitude de référence");
        }
        if (gain < 0 || loss < 0) {
            throw new IllegalArgumentException("Un cumul de dénivelé ne peut pas être négatif");
        }
    }

    public static ElevationSmoother startingAt(Elevation first) {
        return new ElevationSmoother(first, 0, 0);
    }

    public ElevationSmoother accept(Elevation next) {
        double delta = next.differenceWith(reference);
        if (Math.abs(delta) < THRESHOLD_METERS) {
            return this;
        }
        return delta > 0
                ? new ElevationSmoother(next, gain + delta, loss)
                : new ElevationSmoother(next, gain, loss - delta);
    }
}
