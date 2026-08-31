package com.runtrack.notification.usecases.port;

import com.runtrack.notification.usecases.model.inbox.Notification;
import com.runtrack.notification.usecases.model.inbox.NotificationId;
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
     * Écrit ou <b>fait grossir</b> une notification qui résume plusieurs faits.
     *
     * <p>Le second « j'aime » sur une course ne crée pas de ligne : il incrémente le compteur de
     * celle qui existe, la remet en tête et la repasse en non lue. C'est l'agrégation du §7, et
     * elle se fait en une commande — deux « j'aime » simultanés doivent en compter deux.
     *
     * @return la notification telle qu'elle est désormais en base
     */
    Notification aggregate(Notification notification);

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
