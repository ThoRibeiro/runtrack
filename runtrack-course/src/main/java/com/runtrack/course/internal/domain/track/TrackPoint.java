package com.runtrack.course.internal.domain.track;

import com.runtrack.shared.measure.Elevation;
import com.runtrack.shared.measure.GeoPoint;
import java.time.Instant;
import java.util.OptionalInt;

/**
 * Un point de trace tel que le téléphone l'a capturé, une fois son horodatage corrigé de
 * la dérive d'horloge.
 *
 * <p>Le {@code sequenceNumber} vient du client et porte l'idempotence : c'est lui qui
 * permet de rejouer un lot après une coupure réseau sans rien dupliquer ni fausser.
 */
public record TrackPoint(
        int sequenceNumber,
        GeoPoint position,
        Elevation elevation,
        Instant recordedAt,
        double accuracyMeters,
        OptionalInt heartRate,
        OptionalInt cadence) {

    public TrackPoint {
        if (sequenceNumber < 0) {
            throw new IllegalArgumentException("sequenceNumber négatif : " + sequenceNumber);
        }
        if (position == null || elevation == null || recordedAt == null) {
            throw new IllegalArgumentException("TrackPoint incomplet");
        }
        if (heartRate == null || cadence == null) {
            throw new IllegalArgumentException("TrackPoint : utiliser OptionalInt.empty(), pas null");
        }
        if (Double.isNaN(accuracyMeters) || accuracyMeters < 0) {
            throw new IllegalArgumentException("Précision invalide : " + accuracyMeters);
        }
    }
}
