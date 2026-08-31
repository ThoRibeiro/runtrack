package com.runtrack.notification.usecases.model.push;

import com.runtrack.notification.usecases.model.inbox.NotificationType;

/**
 * Le libellé du push, composé au moment de l'envoi.
 *
 * <p>Rien de tout cela n'est stocké avec la notification : le figer à l'écriture fixerait la
 * formulation — et la langue — au moment du fan-out, et rendrait impossible de corriger une
 * tournure autrement qu'en réécrivant l'historique. En base il n'y a que des faits ; les phrases
 * se fabriquent ici.
 *
 * <p>Le nom de l'acteur peut manquer : un compte supprimé entre l'événement et l'envoi ne doit
 * pas empêcher le push de partir, il doit juste le rendre impersonnel.
 */
public final class PushComposer {

    private static final String SOMEONE = "Quelqu'un";

    private PushComposer() {
    }

    /** « Marie », puis « Marie et 1 autre », puis « Marie et 4 autres ». */
    private static String withOthers(String who, int aggregateCount) {
        int others = Math.max(aggregateCount - 1, 0);
        if (others == 0) {
            return who;
        }
        return who + " et " + others + (others == 1 ? " autre" : " autres");
    }

    public static PushMessage compose(NotificationType type, String actorName, String deepLink) {
        return compose(type, actorName, deepLink, 1);
    }

    /**
     * @param aggregateCount le nombre de faits résumés : au-delà de un, le libellé devient
     *     « Marie et 4 autres ont aimé » plutôt que d'annoncer un seul d'entre eux
     */
    public static PushMessage compose(NotificationType type, String actorName, String deepLink,
            int aggregateCount) {

        String who = withOthers(actorName == null || actorName.isBlank() ? SOMEONE : actorName,
                aggregateCount);
        return switch (type) {
            case FRIEND_STARTED_ACTIVITY -> new PushMessage(
                    who + " vient de démarrer une course", "Suivez-la en direct", deepLink);
            case FRIEND_FINISHED_ACTIVITY -> new PushMessage(
                    who + " a terminé sa course", "Voir le résumé", deepLink);
            case NEW_FOLLOWER -> new PushMessage(
                    "Nouvel abonné", who + " suit désormais vos courses", deepLink);
            case FOLLOW_REQUEST -> new PushMessage(
                    "Demande d'abonnement", who + " souhaite suivre vos courses", deepLink);
            case FOLLOW_ACCEPTED -> new PushMessage(
                    "Demande acceptée", who + " a accepté votre demande", deepLink);
            case ACTIVITY_LIKED -> new PushMessage(
                    who + " a aimé votre course", "Voir la course", deepLink);
            case ACTIVITY_COMMENTED -> new PushMessage(
                    who + " a commenté votre course", "Lire le commentaire", deepLink);
            case COMMENT_REPLIED -> new PushMessage(
                    who + " a répondu à votre commentaire", "Lire la réponse", deepLink);
        };
    }
}
