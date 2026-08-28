package com.runtrack.shared;

/**
 * Une altitude, en mètres. Peut être négative : la mer Morte est à −430 m, et un GPS
 * renvoie régulièrement des altitudes sous le niveau de la mer par simple imprécision.
 */
public record Elevation(double meters) implements Comparable<Elevation> {

    public static final Elevation SEA_LEVEL = new Elevation(0);

    public Elevation {
        if (Double.isNaN(meters) || Double.isInfinite(meters)) {
            throw new IllegalArgumentException("Altitude non numérique");
        }
    }

    public static Elevation ofMeters(double meters) {
        return new Elevation(meters);
    }

    public double differenceWith(Elevation other) {
        return meters - other.meters;
    }

    @Override
    public int compareTo(Elevation other) {
        return Double.compare(meters, other.meters);
    }
}
