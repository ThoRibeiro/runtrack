package com.runtrack.notification.internal.application;

import com.runtrack.notification.internal.application.port.NotificationBroadcaster;
import com.runtrack.notification.internal.application.port.NotificationPreferencesRepository;
import com.runtrack.notification.internal.application.port.NotificationRepository;
import com.runtrack.notification.internal.domain.inbox.DeepLink;
import com.runtrack.notification.internal.domain.inbox.Notification;
import com.runtrack.notification.internal.domain.inbox.NotificationAudience;
import com.runtrack.notification.internal.domain.inbox.NotificationId;
import com.runtrack.notification.internal.domain.inbox.NotificationPreferences;
import com.runtrack.notification.internal.domain.inbox.NotificationType;
import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import com.runtrack.social.SocialApi;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La fabrication des notifications, à partir de ce qui vient de se produire ailleurs.
 *
 * <p>Rien ici ne sait qu'un événement Modulith existe : les écouteurs traduisent, ce service
 * décide. C'est ce qui permet de tester le fan-out — la partie qui a des règles — sans contexte
 * Spring ni base.
 *
 * <p>Chaque méthode est transactionnelle et idempotente. Le registre rejoue au redémarrage ce qui
 * n'a pas abouti ; rejouer doit être sans effet, et c'est l'identifiant déduit de l'événement qui
 * le garantit.
 */
@Service
public class NotificationDispatch {

    private final NotificationRepository notifications;
    private final NotificationPreferencesRepository preferences;
    private final NotificationBroadcaster broadcaster;
    private final SocialApi social;

    public NotificationDispatch(NotificationRepository notifications,
            NotificationPreferencesRepository preferences, NotificationBroadcaster broadcaster,
            SocialApi social) {
        this.notifications = notifications;
        this.preferences = preferences;
        this.broadcaster = broadcaster;
        this.social = social;
    }

    /**
     * Le fan-out du §7 : les abonnés acceptés du coureur, moins ceux que ça n'intéresse pas.
     *
     * <p>La liste des abonnés vient de {@code SocialApi}, donc du cache du §6 — c'est le seul
     * appel réseau du chemin, et il est mutualisé pour tous les destinataires.
     */
    @Transactional
    public void runStarted(ActivityId activityId, UserId runnerId, AudienceScope effectiveScope,
            Instant at) {

        broadcast(NotificationType.FRIEND_STARTED_ACTIVITY, runnerId, effectiveScope,
                DeepLink.liveTracking(activityId), activityId.toString(), at);
    }

    @Transactional
    public void runFinished(ActivityId activityId, UserId runnerId, AudienceScope effectiveScope,
            Instant at) {

        broadcast(NotificationType.FRIEND_FINISHED_ACTIVITY, runnerId, effectiveScope,
                DeepLink.activity(activityId), activityId.toString(), at);
    }

    /** Un abonnement immédiat sur un compte ouvert, ou une demande qui vient d'être acceptée. */
    @Transactional
    public void followAccepted(UserId followerId, UserId followeeId, Instant at) {
        // Deux notifications, deux destinataires, deux sens : celui qu'on suit apprend qu'il a un
        // abonné de plus, celui qui suivait apprend qu'on a dit oui.
        notifyOne(NotificationType.NEW_FOLLOWER, followeeId, followerId,
                DeepLink.profile(followerId), followerId.toString(), at);
        notifyOne(NotificationType.FOLLOW_ACCEPTED, followerId, followeeId,
                DeepLink.profile(followeeId), followeeId.toString(), at);
    }

    @Transactional
    public void followRequested(UserId followerId, UserId followeeId, Instant at) {
        notifyOne(NotificationType.FOLLOW_REQUEST, followeeId, followerId,
                DeepLink.followRequests(), followerId.toString(), at);
    }

    private void broadcast(NotificationType type, UserId actorId, AudienceScope effectiveScope,
            String deepLink, String subject, Instant at) {

        Set<UserId> audience = NotificationAudience.forStartedActivity(
                effectiveScope, social.acceptedFollowerIds(actorId));
        if (audience.isEmpty()) {
            return;
        }
        Map<UserId, NotificationPreferences> chosen = preferences.findAll(audience);

        List<Notification> written = notifications.appendAll(audience.stream()
                .filter(recipient -> wants(chosen, recipient, type))
                .map(recipient -> new Notification(
                        NotificationId.deducedFrom(type, recipient, subject, at),
                        recipient, type, java.util.Optional.of(actorId),
                        deepLink, at, java.util.Optional.empty()))
                .toList());

        broadcaster.deliver(written);
    }

    private void notifyOne(NotificationType type, UserId recipientId, UserId actorId,
            String deepLink, String subject, Instant at) {

        if (!wants(preferences.findAll(Set.of(recipientId)), recipientId, type)) {
            return;
        }
        broadcaster.deliver(notifications.appendAll(List.of(Notification.unread(
                NotificationId.deducedFrom(type, recipientId, subject, at),
                recipientId, type, actorId, deepLink, at))));
    }

    /** Sans préférences enregistrées, tout passe : voir {@link NotificationPreferences}. */
    private static boolean wants(Map<UserId, NotificationPreferences> chosen, UserId recipientId,
            NotificationType type) {

        NotificationPreferences settings = chosen.get(recipientId);
        return settings == null || settings.allows(type);
    }
}
