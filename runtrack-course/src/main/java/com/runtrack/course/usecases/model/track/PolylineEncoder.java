package com.runtrack.course.usecases.model.track;

import com.runtrack.shared.measure.GeoPoint;
import java.util.List;

/**
 * L'encodage d'une trace au format <i>encoded polyline</i> de Google, précision 5.
 *
 * <p>Une trace de mille points pèse une quarantaine de kilo-octets en JSON et environ cinq une fois
 * encodée : c'est la différence entre une carte qui s'affiche et une carte qu'on attend. Toutes les
 * bibliothèques cartographiques savent la lire, ce qui évite d'inventer un format à nous.
 *
 * <p>Le principe : on n'envoie que les <b>écarts</b> entre points consécutifs, arrondis au
 * cent-millième de degré — soit environ un mètre. Deux points voisins tiennent alors sur un ou deux
 * caractères là où leurs coordonnées absolues en demandent une vingtaine.
 */
public final class PolylineEncoder {

    /** Cinq décimales : le mètre. Au-delà, on encoderait le bruit du GPS. */
    private static final double PRECISION = 1e5;

    private PolylineEncoder() {
    }

    public static String encode(List<GeoPoint> positions) {
        var encoded = new StringBuilder();
        long previousLatitude = 0;
        long previousLongitude = 0;

        for (GeoPoint position : positions) {
            long latitude = Math.round(position.latitude() * PRECISION);
            long longitude = Math.round(position.longitude() * PRECISION);
            appendSigned(encoded, latitude - previousLatitude);
            appendSigned(encoded, longitude - previousLongitude);
            // Le point de départ du prochain écart est la valeur *arrondie*, pas l'originale :
            // cumuler les arrondis ferait dériver la trace de plusieurs mètres sur une longue sortie.
            previousLatitude = latitude;
            previousLongitude = longitude;
        }
        return encoded.toString();
    }

    private static void appendSigned(StringBuilder encoded, long value) {
        long shifted = value < 0 ? ~(value << 1) : value << 1;
        while (shifted >= 0x20) {
            encoded.append((char) ((0x20 | (shifted & 0x1f)) + 63));
            shifted >>= 5;
        }
        encoded.append((char) (shifted + 63));
    }
}
