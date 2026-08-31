package com.runtrack.notification.internal.infra.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

    protected NotificationPreferencesEntity() {
    }

    public NotificationPreferencesEntity(UUID userId, String muted) {
        this.userId = userId;
        this.muted = muted;
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
