package com.runtrack.notification.infrastructure.events;

import com.runtrack.course.event.ActivityFinished;
import com.runtrack.course.event.ActivityStarted;
import com.runtrack.engagement.event.ActivityCommented;
import com.runtrack.engagement.event.ActivityLiked;
import com.runtrack.engagement.event.CommentReplied;
import com.runtrack.notification.usecases.service.NotificationDispatch;
import com.runtrack.notification.usecases.service.PushDelivery;
import com.runtrack.platform.observability.CorrelationId;
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
 * <p>Aucune règle dans cette classe, seulement de la traduction et un enchaînement. Les décisions
 * — qui recevoir, quoi couper, où mener — sont dans {@link NotificationDispatch}, où elles se
 * testent sans contexte Spring.
 *
 * <p>L'enchaînement, lui, compte : le push part <b>après</b> que la transaction d'écriture s'est
 * refermée, et seulement pour ce qui a réellement été écrit. C'est ainsi que le §7 obtient son
 * « aucun appel réseau dans la transaction », et qu'un rejeu ne repousse rien.
 *
 * <p>Chaque écouteur <b>rouvre la portée de corrélation</b> à partir de l'identifiant que son
 * événement transporte (§12). Sans ce geste, la notification envoyée n'aurait aucun lien traçable
 * avec la requête qui a démarré la course : un {@code ScopedValue} ne franchit pas un changement
 * de fil, et c'est en production qu'on le découvre.
 */
@Component
class NotificationListeners {

    private final NotificationDispatch dispatch;
    private final PushDelivery push;

    NotificationListeners(NotificationDispatch dispatch, PushDelivery push) {
        this.dispatch = dispatch;
        this.push = push;
    }

    @ApplicationModuleListener
    void onActivityStarted(ActivityStarted event) {
        CorrelationId.resume(event.correlationId(), () ->
                push.push(dispatch.runStarted(event.activityId(), event.ownerId(),
                        AudienceScope.valueOf(event.effectiveScope()), event.at())));
    }

    @ApplicationModuleListener
    void onActivityFinished(ActivityFinished event) {
        CorrelationId.resume(event.correlationId(), () ->
                push.push(dispatch.runFinished(event.activityId(), event.ownerId(),
                        AudienceScope.valueOf(event.effectiveScope()), event.at())));
    }

    @ApplicationModuleListener
    void onActivityLiked(ActivityLiked event) {
        CorrelationId.resume(event.correlationId(), () ->
                push.push(dispatch.activityLiked(event.activityId(), event.ownerId(),
                        event.likerId(), event.at())));
    }

    @ApplicationModuleListener
    void onActivityCommented(ActivityCommented event) {
        CorrelationId.resume(event.correlationId(), () ->
                push.push(dispatch.activityCommented(event.activityId(), event.ownerId(),
                        event.authorId(), event.commentId(), event.at())));
    }

    @ApplicationModuleListener
    void onCommentReplied(CommentReplied event) {
        CorrelationId.resume(event.correlationId(), () ->
                push.push(dispatch.commentReplied(event.activityId(), event.parentAuthorId(),
                        event.authorId(), event.commentId(), event.at())));
    }

    @ApplicationModuleListener
    void onFollowAccepted(FollowAccepted event) {
        // Les événements de `social` ne portent pas d'identifiant de corrélation : leur contrat
        // date d'avant le §12. La portée est rouverte quand même, avec un identifiant tiré ici —
        // les journaux de ce traitement restent liés entre eux, à défaut de l'être à la requête.
        CorrelationId.resume(null, () ->
                push.push(dispatch.followAccepted(event.followerId(), event.followeeId(),
                        event.at())));
    }

    @ApplicationModuleListener
    void onFollowRequested(FollowRequested event) {
        CorrelationId.resume(null, () ->
                push.push(dispatch.followRequested(event.followerId(), event.followeeId(),
                        event.at())));
    }
}
