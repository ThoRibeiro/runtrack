package com.runtrack.course.infrastructure.realtime;

import com.runtrack.shared.id.ActivityId;

/**
 * Les clés Dragonfly du direct.
 *
 * <p>Une seule pour l'instant. Le §4 en prévoit une seconde, {@code live:activity:{id}:state} —
 * elle n'est pas écrite : voir {@link RedisLiveActivityPublisher} pour la raison.
 */
public final class LiveKeys {

    private LiveKeys() {
    }

    /** Le journal des événements d'une course, borné en longueur. */
    public static String events(ActivityId activityId) {
        return "live:activity:" + activityId + ":events";
    }
}
