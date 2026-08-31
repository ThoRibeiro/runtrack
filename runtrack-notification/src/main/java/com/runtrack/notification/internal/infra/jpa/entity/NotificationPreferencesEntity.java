package com.runtrack.notification.internal.infra.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.UUID;

/**
 * La table {@code notification_preferences}.
 *
 * <p>Les natures coupées tiennent dans une colonne texte séparée par des virgules, plutôt que
 * dans une table de jointure. Elles sont au plus huit, ne se cherchent jamais autrement que par
 * utilisateur, et une table d'association pour une énumération bornée coûte une jointure à
 * chaque fan-out sans rien apporter.
 */
@Entity
@Table(name = "notification_preferences")
public class NotificationPreferencesEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, columnDefinition = "text")
    private String muted;

    /**
     * Les heures calmes, en trois colonnes plutôt qu'une chaîne.
     *
     * <p>Elles vont ensemble ou pas du tout, et une contrainte de la base le dit : deux colonnes
     * remplies sur trois donneraient une plage dont personne ne saurait quoi faire.
     */
    @Column(name = "quiet_from")
    private LocalTime quietFrom;

    @Column(name = "quiet_to")
    private LocalTime quietTo;

    @Column(name = "quiet_zone", length = 64)
    private String quietZone;

    protected NotificationPreferencesEntity() {
    }

    public NotificationPreferencesEntity(UUID userId, String muted) {
        this.userId = userId;
        this.muted = muted;
    }

    public LocalTime getQuietFrom() {
        return quietFrom;
    }

    public LocalTime getQuietTo() {
        return quietTo;
    }

    public String getQuietZone() {
        return quietZone;
    }

    public void quietHours(LocalTime from, LocalTime to, String zone) {
        this.quietFrom = from;
        this.quietTo = to;
        this.quietZone = zone;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getMuted() {
        return muted;
    }

    public void mute(String value) {
        this.muted = value;
    }
}
