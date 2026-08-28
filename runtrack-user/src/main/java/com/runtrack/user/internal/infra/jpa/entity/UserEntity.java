package com.runtrack.user.internal.infra.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * La table {@code users}. Ne sort jamais de {@code internal/infra} : c'est la couche REST
 * qui expose des DTO, et le domaine qui porte les invariants.
 *
 * <p>Les énumérations sont persistées en chaîne, jamais en ordinal : un {@code ORDINAL}
 * transforme une réorganisation de l'énumération en corruption silencieuse des données.
 */
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String handle;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    private String bio;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_scope", nullable = false)
    private String accountScope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private String status;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "biological_sex")
    private String biologicalSex;

    @Column(name = "weight_kilograms")
    private Double weightKilograms;

    @Column(name = "height_centimeters")
    private Double heightCentimeters;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Version
    private long version;

    protected UserEntity() {
        // Requis par JPA.
    }

    public UserEntity(UUID id, String handle, String email, String displayName, String avatarUrl,
            String bio, String accountScope, String status, LocalDate birthDate, String biologicalSex,
            Double weightKilograms, Double heightCentimeters, Instant registeredAt) {
        this.id = id;
        this.handle = handle;
        this.email = email;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.bio = bio;
        this.accountScope = accountScope;
        this.status = status;
        this.birthDate = birthDate;
        this.biologicalSex = biologicalSex;
        this.weightKilograms = weightKilograms;
        this.heightCentimeters = heightCentimeters;
        this.registeredAt = registeredAt;
    }

    public UUID getId() {
        return id;
    }

    public String getHandle() {
        return handle;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getBio() {
        return bio;
    }

    public String getAccountScope() {
        return accountScope;
    }

    public String getStatus() {
        return status;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getBiologicalSex() {
        return biologicalSex;
    }

    public Double getWeightKilograms() {
        return weightKilograms;
    }

    public Double getHeightCentimeters() {
        return heightCentimeters;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public void refreshFrom(UserEntity other) {
        this.handle = other.handle;
        this.email = other.email;
        this.displayName = other.displayName;
        this.avatarUrl = other.avatarUrl;
        this.bio = other.bio;
        this.accountScope = other.accountScope;
        this.status = other.status;
        this.birthDate = other.birthDate;
        this.biologicalSex = other.biologicalSex;
        this.weightKilograms = other.weightKilograms;
        this.heightCentimeters = other.heightCentimeters;
    }
}
