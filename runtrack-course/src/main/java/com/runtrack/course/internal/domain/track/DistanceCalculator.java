package com.runtrack.course.internal.domain.track;

import com.runtrack.shared.measure.Distance;
import com.runtrack.shared.measure.GeoPoint;

/**
 * La distance entre deux positions, par la formule de haversine.
 *
 * <p>Précision retenue : haversine assimile la Terre à une sphère de rayon moyen, ce qui
 * introduit une erreur allant jusqu'à ~0,5 % aux latitudes extrêmes par rapport à un
 * calcul géodésique (Vincenty). Sur des segments de quelques mètres — ce que produit un
 * GPS toutes les secondes — l'écart est très inférieur à l'imprécision du capteur
 * lui-même. La complexité de Vincenty ne se paierait pas ici.
 */
public final class DistanceCalculator {

    /** Rayon terrestre moyen IUGG, en mètres. */
    static final double EARTH_RADIUS_METERS = 6_371_008.8;

    private DistanceCalculator() {
    }

    public static Distance between(GeoPoint from, GeoPoint to) {
        double fromLatitude = Math.toRadians(from.latitude());
        double toLatitude = Math.toRadians(to.latitude());
        double deltaLatitude = Math.toRadians(to.latitude() - from.latitude());
        double deltaLongitude = Math.toRadians(to.longitude() - from.longitude());

        double a = Math.pow(Math.sin(deltaLatitude / 2), 2)
                + Math.cos(fromLatitude) * Math.cos(toLatitude) * Math.pow(Math.sin(deltaLongitude / 2), 2);
        double centralAngle = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return Distance.ofMeters(EARTH_RADIUS_METERS * centralAngle);
    }
}
