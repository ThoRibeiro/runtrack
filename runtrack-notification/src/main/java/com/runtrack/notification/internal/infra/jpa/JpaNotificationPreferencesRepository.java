package com.runtrack.notification.internal.infra.jpa;

import com.runtrack.notification.internal.application.port.NotificationPreferencesRepository;
import com.runtrack.notification.internal.domain.inbox.NotificationPreferences;
import com.runtrack.notification.internal.domain.inbox.NotificationType;
import com.runtrack.notification.internal.domain.push.QuietHours;
import com.runtrack.notification.internal.infra.jpa.entity.NotificationPreferencesEntity;
import com.runtrack.shared.id.UserId;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/**
 * Les préférences, en JPA : peu de lignes, écrites une par une, lues par lot au fan-out.
 *
 * <p>Contrairement aux notifications elles-mêmes, elles se modifient — un {@code EntityManager}
 * est ici exactement le bon outil.
 */
@Repository
class JpaNotificationPreferencesRepository implements NotificationPreferencesRepository {

    private static final Logger LOG = LoggerFactory.getLogger(JpaNotificationPreferencesRepository.class);

    private final SpringDataPreferencesRepository entities;

    JpaNotificationPreferencesRepository(SpringDataPreferencesRepository entities) {
        this.entities = entities;
    }

    @Override
    public Optional<NotificationPreferences> find(UserId userId) {
        return entities.findById(userId.value()).map(JpaNotificationPreferencesRepository::toDomain);
    }

    @Override
    public Map<UserId, NotificationPreferences> findAll(Collection<UserId> userIds) {
        var found = new LinkedHashMap<UserId, NotificationPreferences>();
        entities.findAllById(userIds.stream().map(UserId::value).toList())
                .forEach(entity -> found.put(new UserId(entity.getUserId()), toDomain(entity)));
        return Map.copyOf(found);
    }

    @Override
    public void save(NotificationPreferences preferences) {
        UUID userId = preferences.userId().value();
        String muted = preferences.muted().stream()
                .map(NotificationType::name)
                .collect(Collectors.joining(","));

        NotificationPreferencesEntity entity = entities.findById(userId)
                .orElseGet(() -> new NotificationPreferencesEntity(userId, muted));
        entity.mute(muted);
        preferences.quietHours().ifPresentOrElse(
                hours -> entity.quietHours(hours.from(), hours.to(), hours.zone().getId()),
                () -> entity.quietHours(null, null, null));
        entities.save(entity);
    }

    private static NotificationPreferences toDomain(NotificationPreferencesEntity entity) {
        return new NotificationPreferences(
                new UserId(entity.getUserId()), parse(entity.getMuted()), quietHoursOf(entity));
    }

    /**
     * Un fuseau devenu inconnu vaut « pas d'heures calmes ».
     *
     * <p>La base des fuseaux change — des zones disparaissent — et refuser de lire les préférences
     * pour autant empêcherait le destinataire de recevoir quoi que ce soit. Le pire cas est un push
     * pendant la nuit, pas un silence définitif.
     */
    private static Optional<QuietHours> quietHoursOf(NotificationPreferencesEntity entity) {
        if (entity.getQuietFrom() == null || entity.getQuietTo() == null || entity.getQuietZone() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new QuietHours(
                    entity.getQuietFrom(), entity.getQuietTo(), ZoneId.of(entity.getQuietZone())));
        } catch (RuntimeException unusable) {
            LOG.debug("Heures calmes illisibles pour {} : {}", entity.getUserId(), unusable.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Une nature inconnue est ignorée, pas fatale.
     *
     * <p>Le cas se produit à la suppression d'un type : les lignes qui le mentionnent survivent à
     * la version qui l'a retiré, et faire échouer la lecture des préférences empêcherait le
     * destinataire de recevoir quoi que ce soit.
     */
    private static Set<NotificationType> parse(String muted) {
        if (muted == null || muted.isBlank()) {
            return EnumSet.noneOf(NotificationType.class);
        }
        var types = EnumSet.noneOf(NotificationType.class);
        Arrays.stream(muted.split(",")).map(String::trim).filter(name -> !name.isEmpty())
                .forEach(name -> {
                    try {
                        types.add(NotificationType.valueOf(name));
                    } catch (IllegalArgumentException unknown) {
                        LOG.debug("Préférence obsolète ignorée : {}", name);
                    }
                });
        return types;
    }
}
