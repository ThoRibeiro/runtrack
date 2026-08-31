package com.runtrack.course.internal.domain.live;

import com.runtrack.course.internal.domain.stats.ActivityStats;
import com.runtrack.course.internal.domain.track.TrackPoint;
import com.runtrack.shared.measure.Elevation;
import com.runtrack.shared.measure.GeoPoint;
import java.time.Instant;
import java.util.OptionalInt;

/**
 * Ce qu'un spectateur reçoit pendant qu'une course se déroule.
 *
 * <p>Scellé : le jour où un type d'événement s'ajoute, le compilateur signale chaque endroit
 * qui l'ignore — le codec du stream comme la traduction SSE.
 *
 * <p>{@link #kind()} nomme la nature de l'événement et non son transport. Le SSE s'en sert
 * comme nom d'événement, mais ce n'est pas SSE qui décide : un même événement voyage aussi
 * dans le Stream Dragonfly, où « position » a exactement le même sens.
 */
public sealed interface LiveEvent {

    String kind();

    /** Une position de plus sur la carte du spectateur. */
    record Position(
            int sequenceNumber,
            GeoPoint position,
            Elevation elevation,
            Instant recordedAt,
            OptionalInt heartRate) implements LiveEvent {

        public Position {
            if (position == null || elevation == null || recordedAt == null || heartRate == null) {
                throw new IllegalArgumentException("Position incomplète");
            }
        }

        /**
         * Ne retient du point que ce qui s'affiche.
         *
         * <p>La précision et la cadence servent au filtrage et aux statistiques, pas au suivi :
         * les diffuser à des tiers, c'est publier des données de capteur sans usage.
         */
        public static Position of(TrackPoint point) {
            return new Position(point.sequenceNumber(), point.position(), point.elevation(),
                    point.recordedAt(), point.heartRate());
        }

        @Override
        public String kind() {
            return "position";
        }
    }

    /** Les statistiques recalculées après un lot de points. */
    record Stats(ActivityStats stats) implements LiveEvent {

        public Stats {
            if (stats == null) {
                throw new IllegalArgumentException("Statistiques absentes");
            }
        }

        @Override
        public String kind() {
            return "stats";
        }
    }

    /** Une transition du cycle de vie : mise en pause, reprise, fin, abandon. */
    record Status(String status, Instant since) implements LiveEvent {

        public Status {
            if (status == null || status.isBlank() || since == null) {
                throw new IllegalArgumentException("Transition incomplète");
            }
        }

        @Override
        public String kind() {
            return "status";
        }
    }
}
