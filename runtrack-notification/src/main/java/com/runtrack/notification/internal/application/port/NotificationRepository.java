package com.runtrack.notification.internal.application.port;

import com.runtrack.notification.internal.domain.inbox.Notification;
import com.runtrack.notification.internal.domain.inbox.NotificationId;
import com.runtrack.shared.id.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** La boîte de réception en base. */
public interface NotificationRepository {

    /**
     * Écrit un lot, en ignorant ce qui existe déjà.
     *
     * <p>C'est le second filet de l'idempotence du §7. L'identifiant est déduit de l'événement,
     * donc un rejeu présente les mêmes identifiants ; la base les refuse sans faire échouer le
     * lot, et le rejeu n'a aucun effet visible.
     *
     * @return les notifications réellement insérées — seules celles-là ont à être diffusées
     */
    List<Notification> appendAll(List<Notification> notifications);

    /**
     * Une page de la boîte, de la plus récente à la plus ancienne.
     *
     * @param before curseur : la date de création de la dernière notification déjà reçue
     */
    List<Notification> findFor(UserId recipientId, Optional<Instant> before, boolean unreadOnly, int limit);

    Optional<Notification> find(UserId recipientId, NotificationId id);

    /** @return {@code true} si la notification était non lue et vient d'être marquée */
    boolean markRead(UserId recipientId, NotificationId id, Instant when);

    /** @return le nombre de notifications que cet appel a fait passer en lu */
    int markAllRead(UserId recipientId, Instant when);

    long unreadCount(UserId recipientId);
}
