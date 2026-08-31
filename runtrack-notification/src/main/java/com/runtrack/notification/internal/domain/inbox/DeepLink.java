package com.runtrack.notification.internal.domain.inbox;

import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;

/**
 * Où la notification emmène quand on la touche.
 *
 * <p>Le §7 est explicite sur le cas qui compte : « ton ami vient de démarrer une course » ouvre
 * <b>le suivi live</b>, pas la fiche de la course. Une notification qui demande deux gestes de
 * plus pour arriver à ce qu'elle annonce n'a pas rempli son office.
 *
 * <p>Des chemins, pas des URL absolues : le schéma et l'hôte appartiennent au client, qui sait
 * s'il ouvre une application ou un navigateur.
 */
public final class DeepLink {

    private DeepLink() {
    }

    public static String liveTracking(ActivityId activityId) {
        return "/activities/" + activityId + "/live";
    }

    public static String activity(ActivityId activityId) {
        return "/activities/" + activityId;
    }

    public static String profile(UserId userId) {
        return "/users/" + userId;
    }

    public static String followRequests() {
        return "/me/follow-requests";
    }

    /** Un commentaire s'ouvre dans le fil de sa course, pas sur une page à lui. */
    public static String comment(ActivityId activityId, String commentId) {
        return "/activities/" + activityId + "/comments#" + commentId;
    }
}
