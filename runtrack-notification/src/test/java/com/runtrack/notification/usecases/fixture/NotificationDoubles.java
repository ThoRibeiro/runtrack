package com.runtrack.notification.usecases.fixture;

import com.runtrack.notification.usecases.port.NotificationBroadcaster;
import com.runtrack.notification.usecases.port.NotificationPreferencesRepository;
import com.runtrack.notification.usecases.port.DeviceTokenRepository;
import com.runtrack.notification.usecases.port.NotificationRepository;
import com.runtrack.notification.usecases.port.PushSender;
import com.runtrack.notification.usecases.port.PushThrottle;
import com.runtrack.notification.usecases.model.inbox.Notification;
import com.runtrack.notification.usecases.model.inbox.NotificationId;
import com.runtrack.notification.usecases.model.inbox.NotificationPreferences;
import com.runtrack.notification.usecases.model.inbox.NotificationType;
import com.runtrack.notification.usecases.model.push.DeviceToken;
import com.runtrack.notification.usecases.model.push.PushMessage;
import com.runtrack.shared.id.UserId;
import com.runtrack.social.SocialApi;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Doubles en mémoire des ports de {@code notification}. */
public final class NotificationDoubles {

    private NotificationDoubles() {
    }

    /**
     * La boîte en mémoire, avec la même clé primaire que la table.
     *
     * <p>La carte reproduit le {@code ON CONFLICT DO NOTHING} : sans cela le double laisserait
     * passer les rejeux que la base refuse, et le test prouverait le contraire de la production.
     */
    public static final class Notifications implements NotificationRepository {

        private final Map<NotificationId, Notification> stored = new LinkedHashMap<>();

        @Override
        public List<Notification> appendAll(List<Notification> notifications) {
            var inserted = new ArrayList<Notification>();
            notifications.forEach(notification -> {
                if (stored.putIfAbsent(notification.id(), notification) == null) {
                    inserted.add(notification);
                }
            });
            return List.copyOf(inserted);
        }

        /** Reproduit l'{@code ON CONFLICT DO UPDATE} : la ligne existe, son compteur avance. */
        @Override
        public Notification aggregate(Notification notification) {
            Notification existing = stored.get(notification.id());
            Notification updated = existing == null ? notification : new Notification(
                    notification.id(), notification.recipientId(), notification.type(),
                    notification.actorId(), notification.deepLink(), notification.createdAt(),
                    Optional.empty(), existing.aggregateCount() + 1);
            stored.put(updated.id(), updated);
            return updated;
        }

        @Override
        public List<Notification> findFor(UserId recipientId, Optional<Instant> before,
                boolean unreadOnly, int limit) {

            return stored.values().stream()
                    .filter(notification -> notification.recipientId().equals(recipientId))
                    .filter(notification -> !unreadOnly || notification.isUnread())
                    .filter(notification -> before
                            .map(cursor -> notification.createdAt().isBefore(cursor)).orElse(true))
                    .sorted(Comparator.comparing(Notification::createdAt).reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public Optional<Notification> find(UserId recipientId, NotificationId id) {
            return Optional.ofNullable(stored.get(id))
                    .filter(notification -> notification.recipientId().equals(recipientId));
        }

        @Override
        public boolean markRead(UserId recipientId, NotificationId id, Instant when) {
            return find(recipientId, id).filter(Notification::isUnread).map(notification -> {
                stored.put(id, notification.readAt(when));
                return true;
            }).orElse(false);
        }

        @Override
        public int markAllRead(UserId recipientId, Instant when) {
            List<Notification> unread = stored.values().stream()
                    .filter(notification -> notification.recipientId().equals(recipientId))
                    .filter(Notification::isUnread)
                    .toList();
            unread.forEach(notification -> stored.put(notification.id(), notification.readAt(when)));
            return unread.size();
        }

        @Override
        public long unreadCount(UserId recipientId) {
            return stored.values().stream()
                    .filter(notification -> notification.recipientId().equals(recipientId))
                    .filter(Notification::isUnread)
                    .count();
        }

        public int size() {
            return stored.size();
        }
    }

    public static final class Preferences implements NotificationPreferencesRepository {

        private final Map<UserId, NotificationPreferences> stored = new LinkedHashMap<>();

        @Override
        public Optional<NotificationPreferences> find(UserId userId) {
            return Optional.ofNullable(stored.get(userId));
        }

        @Override
        public Map<UserId, NotificationPreferences> findAll(Collection<UserId> userIds) {
            var found = new LinkedHashMap<UserId, NotificationPreferences>();
            userIds.forEach(userId -> find(userId).ifPresent(settings -> found.put(userId, settings)));
            return Map.copyOf(found);
        }

        @Override
        public void save(NotificationPreferences preferences) {
            stored.put(preferences.userId(), preferences);
        }
    }

    /** Retient ce qui est réellement parti en temps réel. */
    public static final class Broadcaster implements NotificationBroadcaster {

        private final List<Notification> delivered = new ArrayList<>();

        @Override
        public void deliver(List<Notification> notifications) {
            delivered.addAll(notifications);
        }

        public List<Notification> delivered() {
            return List.copyOf(delivered);
        }
    }

    /** Les appareils en mémoire, avec la même clé que la table : le jeton. */
    public static final class Devices implements DeviceTokenRepository {

        private final Map<String, DeviceToken> stored = new LinkedHashMap<>();

        @Override
        public void register(DeviceToken device) {
            stored.put(device.token(), device);
        }

        @Override
        public boolean forget(UserId ownerId, String token) {
            return Optional.ofNullable(stored.get(token))
                    .filter(device -> device.ownerId().equals(ownerId))
                    .map(device -> stored.remove(token) != null)
                    .orElse(false);
        }

        @Override
        public List<DeviceToken> of(UserId ownerId) {
            return stored.values().stream()
                    .filter(device -> device.ownerId().equals(ownerId))
                    .toList();
        }

        @Override
        public List<DeviceToken> ofAll(Collection<UserId> ownerIds) {
            return stored.values().stream()
                    .filter(device -> ownerIds.contains(device.ownerId()))
                    .toList();
        }

        @Override
        public int forgetAll(Collection<String> tokens) {
            int before = stored.size();
            tokens.forEach(stored::remove);
            return before - stored.size();
        }

        public int size() {
            return stored.size();
        }
    }

    /** Retient chaque envoi, et rend invalides les jetons qu'on lui désigne d'avance. */
    public static final class Sender implements PushSender {

        private final List<Sent> sent = new ArrayList<>();
        private final Set<String> invalid = new LinkedHashSet<>();

        public record Sent(List<DeviceToken> devices, PushMessage message) {
        }

        public Sender rejecting(String... tokens) {
            invalid.addAll(List.of(tokens));
            return this;
        }

        @Override
        public Result send(List<DeviceToken> devices, PushMessage message) {
            sent.add(new Sent(List.copyOf(devices), message));
            Set<String> rejected = devices.stream().map(DeviceToken::token)
                    .filter(invalid::contains)
                    .collect(java.util.stream.Collectors.toSet());
            return new Result(devices.size() - rejected.size(), rejected);
        }

        public List<Sent> sent() {
            return List.copyOf(sent);
        }
    }

    /** Un garde-fou qui laisse passer une fois par clé, comme celui de Dragonfly. */
    public static final class Throttle implements PushThrottle {

        private final Set<String> armed = new LinkedHashSet<>();
        private boolean alwaysAllow;

        public Throttle allowingEverything() {
            this.alwaysAllow = true;
            return this;
        }

        @Override
        public boolean allow(UserId actorId, UserId recipientId, NotificationType type,
                Duration window) {

            return alwaysAllow || armed.add(type.name() + actorId + recipientId);
        }
    }

    /** Ne répond qu'à la question que le push pose : sous quel nom afficher l'acteur. */
    public static class Users implements com.runtrack.user.UserApi {

        @Override
        public UserId register(com.runtrack.user.NewUser newUser) {
            throw new UnsupportedOperationException("Hors du périmètre de notification");
        }

        @Override
        public boolean ensureProfile(UserId id, com.runtrack.user.FederatedProfile profile) {
            throw new UnsupportedOperationException("Hors du périmètre de notification");
        }

        @Override
        public void confirmEmail(UserId id) {
            throw new UnsupportedOperationException("Hors du périmètre de notification");
        }

        @Override
        public Optional<UserId> idOfEmail(String email) {
            return Optional.empty();
        }

        @Override
        public boolean exists(UserId id) {
            return true;
        }

        @Override
        public Optional<com.runtrack.user.UserSummary> summary(UserId id) {
            return Optional.empty();
        }

        @Override
        public Map<UserId, com.runtrack.user.UserSummary> summaries(Collection<UserId> ids) {
            return Map.of();
        }

        @Override
        public Optional<com.runtrack.shared.access.AudienceScope> accountScope(UserId id) {
            return Optional.of(com.runtrack.shared.access.AudienceScope.PUBLIC);
        }

        @Override
        public Optional<com.runtrack.user.RunnerMass> massOf(UserId id) {
            return Optional.empty();
        }
    }

    /** Un graphe social piloté à la main : c'est la liste d'abonnés que le test fait varier. */
    public static final class Social implements SocialApi {

        private final Set<UserId> followers = new LinkedHashSet<>();

        public Social withFollowers(UserId... userIds) {
            followers.addAll(List.of(userIds));
            return this;
        }

        @Override
        public Set<UserId> acceptedFollowerIds(UserId userId) {
            return Set.copyOf(followers);
        }

        @Override
        public Set<UserId> acceptedFolloweeIds(UserId userId) {
            return Set.of();
        }

        @Override
        public boolean isFollowing(UserId followerId, UserId followeeId) {
            return followers.contains(followerId);
        }

        @Override
        public boolean isBlockedEitherWay(UserId one, UserId other) {
            return false;
        }
    }
}
