package com.runtrack.notification.internal.domain.inbox;

/**
 * Les huit natures de notification du §7.
 *
 * <p>Trois d'entre elles — celles qui concernent les likes et les commentaires — n'ont pas encore
 * de producteur : le module {@code engagement} viendra plus tard. Elles sont déclarées quand même,
 * parce que c'est l'énumération qui définit ce qu'un destinataire peut couper dans ses préférences,
 * et qu'une préférence qui apparaît après coup arrive toujours activée chez ceux qui l'auraient
 * refusée.
 */
public enum NotificationType {

    FRIEND_STARTED_ACTIVITY,
    FRIEND_FINISHED_ACTIVITY,
    NEW_FOLLOWER,
    FOLLOW_REQUEST,
    FOLLOW_ACCEPTED,
    ACTIVITY_LIKED,
    ACTIVITY_COMMENTED,
    COMMENT_REPLIED
}
