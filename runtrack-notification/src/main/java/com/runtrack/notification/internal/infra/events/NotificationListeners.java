package com.runtrack.notification.internal.infra.events;

import com.runtrack.course.event.ActivityFinished;
import com.runtrack.course.event.ActivityStarted;
import com.runtrack.notification.internal.application.NotificationDispatch;
import com.runtrack.shared.access.AudienceScope;
import com.runtrack.social.event.FollowAccepted;
import com.runtrack.social.event.FollowRequested;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * L'entrée du module : ce que les autres publient, traduit en gestes de notification.
 *
 * <p>{@code @ApplicationModuleListener} apporte d'un coup ce que le §7 interdit d'écrire à la
 * main : exécution <b>après le commit</b> de la transaction métier, sur un autre fil, avec
 * l'événement persisté dans le registre <b>avant</b> traitement et marqué complété après. Un
 * traitement qui échoue reste donc en souffrance, et il est rejoué — au redémarrage comme par la
 * reprise périodique. C'est l'outbox transactionnelle, sans table maison.
 *
 * <p>Conséquence directe : un démarrage de course ne peut ni ralentir ni échouer à cause de ce
 * qui se passe ici. Le coureur a commencé à courir avant que la première notification soit écrite.
 *
 * <p>Aucune règle dans cette classe, seulement de la traduction. Les décisions — qui recevoir,
 * quoi couper, où mener — sont dans {@link NotificationDispatch}, où elles se testent sans
 * contexte Spring.
 */
@Component
class NotificationListeners {

    private final NotificationDispatch dispatch;

    NotificationListeners(NotificationDispatch dispatch) {
        this.dispatch = dispatch;
    }

    @ApplicationModuleListener
    void onActivityStarted(ActivityStarted event) {
        dispatch.runStarted(event.activityId(), event.ownerId(),
                AudienceScope.valueOf(event.effectiveScope()), event.at());
    }

    @ApplicationModuleListener
    void onActivityFinished(ActivityFinished event) {
        dispatch.runFinished(event.activityId(), event.ownerId(),
                AudienceScope.valueOf(event.effectiveScope()), event.at());
    }

    @ApplicationModuleListener
    void onFollowAccepted(FollowAccepted event) {
        dispatch.followAccepted(event.followerId(), event.followeeId(), event.at());
    }

    @ApplicationModuleListener
    void onFollowRequested(FollowRequested event) {
        dispatch.followRequested(event.followerId(), event.followeeId(), event.at());
    }
}
