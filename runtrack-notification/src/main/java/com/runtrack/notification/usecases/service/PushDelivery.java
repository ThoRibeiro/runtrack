package com.runtrack.notification.usecases.service;

import com.runtrack.notification.usecases.port.DeviceTokenRepository;
import com.runtrack.notification.usecases.port.NotificationPreferencesRepository;
import com.runtrack.notification.usecases.port.PushSender;
import com.runtrack.notification.usecases.port.PushThrottle;
import com.runtrack.notification.usecases.model.inbox.Notification;
import com.runtrack.notification.usecases.model.inbox.NotificationPreferences;
import com.runtrack.notification.usecases.model.push.DeviceToken;
import com.runtrack.notification.usecases.model.push.PushComposer;
import com.runtrack.notification.usecases.model.push.PushMessage;
import com.runtrack.shared.id.UserId;
import com.runtrack.user.UserApi;
import com.runtrack.user.UserSummary;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * L'envoi des push, une fois les notifications écrites.
 *
 * <p>Appelé depuis l'écouteur Modulith, donc <b>après le commit de la transaction métier et sur un
 * autre fil</b> : c'est l'exigence du §7 la plus facile à trahir sans s'en apercevoir. Un
 * démarrage de course ne doit ni ralentir ni échouer parce que Firebase rame.
 *
 * <p>Trois filtres se succèdent, et l'ordre est celui du moins cher au plus cher : les préférences
 * (déjà en mémoire), les heures calmes (un calcul), le garde-fou anti-spam (un aller-retour
 * Dragonfly). Les appareils ne sont chargés qu'ensuite, pour ceux qui restent.
 *
 * <p>Rien ici ne relance : un push perdu est perdu. Il est doublé par la notification en base, que
 * le destinataire verra en rouvrant l'application, et réessayer une interruption avec plusieurs
 * minutes de retard n'a plus d'intérêt.
 */
@Service
public class PushDelivery {

    /** §7 : au plus un push par couple (coureur, destinataire) et par nature sur 30 minutes. */
    static final Duration ANTI_SPAM_WINDOW = Duration.ofMinutes(30);

    private static final Logger LOG = LoggerFactory.getLogger(PushDelivery.class);

    private final DeviceTokenRepository devices;
    private final NotificationPreferencesRepository preferences;
    private final PushThrottle throttle;
    private final PushSender sender;
    private final UserApi users;
    private final java.time.Clock clock;
    private final Counter delivered;
    private final Counter failed;

    public PushDelivery(DeviceTokenRepository devices, NotificationPreferencesRepository preferences,
            PushThrottle throttle, PushSender sender, UserApi users, java.time.Clock clock,
            MeterRegistry meters) {
        this.devices = devices;
        this.preferences = preferences;
        this.throttle = throttle;
        this.sender = sender;
        this.users = users;
        this.clock = clock;
        this.delivered = Counter.builder("runtrack.push.delivered")
                .description("Appareils que le service de push a acceptés").register(meters);
        // Le §12 le demande nommément, et pour une bonne raison : un push qui n'arrive plus est
        // invisible côté serveur — personne ne se plaint d'une notification qu'il n'a pas reçue.
        this.failed = Counter.builder("runtrack.push.failed")
                .description("Appareils que le service de push a refusés").register(meters);
    }

    /**
     * L'heure est celle de maintenant, jamais celle de l'événement.
     *
     * <p>Un événement rejoué au redémarrage peut dater de plusieurs heures : le juger sur son
     * horodatage d'origine réveillerait quelqu'un au motif qu'il ne dormait pas encore quand la
     * course a démarré.
     */
    public void push(List<Notification> notifications) {
        if (notifications.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        Map<UserId, NotificationPreferences> chosen = preferences.findAll(
                notifications.stream().map(Notification::recipientId).collect(Collectors.toSet()));

        List<Notification> worthSending = notifications.stream()
                .filter(notification -> acceptsPushNow(chosen, notification, now))
                .filter(this::passesAntiSpam)
                .toList();

        if (!worthSending.isEmpty()) {
            deliver(worthSending);
        }
    }

    /**
     * Un envoi par message distinct, et non par destinataire.
     *
     * <p>Un fan-out produit le même message pour tous les abonnés d'un coureur : les regrouper est
     * ce qui transforme mille appels en un seul lot. C'est le « jamais un appel par ami » du §7.
     */
    private void deliver(List<Notification> notifications) {
        Map<UserId, String> names = displayNames(notifications);
        Map<PushMessage, List<UserId>> byMessage = notifications.stream().collect(Collectors.groupingBy(
                notification -> PushComposer.compose(
                        notification.type(),
                        notification.actorId().map(names::get).orElse(null),
                        notification.deepLink(),
                        notification.aggregateCount()),
                Collectors.mapping(Notification::recipientId, Collectors.toList())));

        var invalid = new java.util.HashSet<String>();
        byMessage.forEach((message, recipients) -> {
            List<DeviceToken> targets = devices.ofAll(recipients);
            if (targets.isEmpty()) {
                return;
            }
            PushSender.Result result = sender.send(targets, message);
            delivered.increment(result.delivered());
            failed.increment(targets.size() - result.delivered());
            invalid.addAll(result.invalidTokens());
        });

        purge(invalid);
    }

    /**
     * Les noms des acteurs, en une requête pour tout le lot.
     *
     * <p>Un appel par notification serait le N+1 du §10, et il tomberait précisément sur le chemin
     * qu'un fan-out emprunte des centaines de fois.
     */
    private Map<UserId, String> displayNames(List<Notification> notifications) {
        Set<UserId> actors = notifications.stream()
                .map(Notification::actorId)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());

        return users.summaries(actors).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().displayName()));
    }

    private boolean acceptsPushNow(Map<UserId, NotificationPreferences> chosen, Notification notification,
            Instant now) {

        NotificationPreferences settings = chosen.get(notification.recipientId());
        return settings == null || settings.acceptsPushAt(now);
    }

    /**
     * Le garde-fou ne s'applique qu'au push, jamais à la boîte de réception.
     *
     * <p>Trois courses démarrées en dix minutes, ce sont trois faits : la boîte les garde, et
     * l'écran saura les regrouper. Le push, lui, est une interruption — la deuxième en dix minutes
     * n'apprend rien de plus et coûte l'attention de quelqu'un.
     *
     * <p>Pour les « j'aime », c'est le <b>sujet</b> qui borne, pas l'auteur : vingt personnes qui
     * aiment la même course ne doivent produire qu'un push, dont le libellé porte le total. La clé
     * est donc la course elle-même, ce que rend la notification agrégée en désignant son propre
     * destinataire comme acteur de la fenêtre.
     */
    private boolean passesAntiSpam(Notification notification) {
        return notification.actorId()
                .map(actor -> throttle.allow(
                        actor, notification.recipientId(), notification.type(), ANTI_SPAM_WINDOW))
                .orElse(true);
    }

    private void purge(Collection<String> invalidTokens) {
        if (invalidTokens.isEmpty()) {
            return;
        }
        int forgotten = devices.forgetAll(invalidTokens);
        LOG.info("{} jeton(s) d'appareil invalides effacés", forgotten);
    }
}
