package com.runtrack.shared.measure;

/** Une distance, toujours positive ou nulle. */
public record Distance(double meters) implements Comparable<Distance> {

    public static final Distance ZERO = new Distance(0);
    public static final double METERS_PER_KILOMETER = 1_000d;

    public Distance {
        if (Double.isNaN(meters) || Double.isInfinite(meters)) {
            throw new IllegalArgumentException("Distance non numérique");
        }
        if (meters < 0) {
            throw new IllegalArgumentException("Distance négative : " + meters);
        }
    }

    public static Distance ofMeters(double meters) {
        return new Distance(meters);
    }

    public static Distance ofKilometers(double kilometers) {
        return new Distance(kilometers * METERS_PER_KILOMETER);
    }

    public double toKilometers() {
        return meters / METERS_PER_KILOMETER;
    }

    public Distance plus(Distance other) {
        return new Distance(meters + other.meters);
    }

    public boolean isZero() {
        return meters == 0;
    }

    @Override
    public int compareTo(Distance other) {
        return Double.compare(meters, other.meters);
    }
}
