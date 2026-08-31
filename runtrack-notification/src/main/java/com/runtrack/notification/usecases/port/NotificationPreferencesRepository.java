package com.runtrack.notification.usecases.port;

import com.runtrack.notification.usecases.model.inbox.NotificationPreferences;
import com.runtrack.shared.id.UserId;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** Les préférences enregistrées. Leur absence vaut « tout activé », pas « rien ». */
public interface NotificationPreferencesRepository {

    Optional<NotificationPreferences> find(UserId userId);

    /**
     * Les préférences de tout un fan-out, en une requête.
     *
     * <p>Les comptes qui n'ont jamais rien réglé sont simplement absents de la carte rendue :
     * écrire une ligne par compte créé pour représenter « je n'ai rien coupé » ferait une table
     * aussi grande que celle des utilisateurs, pour n'y stocker que du vide.
     */
    Map<UserId, NotificationPreferences> findAll(Collection<UserId> userIds);

    void save(NotificationPreferences preferences);
}
