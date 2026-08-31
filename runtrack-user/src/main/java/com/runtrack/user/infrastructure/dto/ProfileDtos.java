package com.runtrack.user.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Les contrats HTTP du module. Séparés du modèle persistant sans exception : le contrat
 * d'API ne doit pas bouger parce qu'une colonne bouge.
 */
public final class ProfileDtos {

    private ProfileDtos() {
    }

    /** Le profil complet, réservé à son propriétaire. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MyProfile(
            String id,
            String handle,
            String email,
            String displayName,
            String avatarUrl,
            String bio,
            String accountScope,
            String status,
            Instant registeredAt) {
    }

    /** Ce que voit un tiers : ni adresse e-mail, ni état de compte, ni physiologie. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PublicProfile(
            String id,
            String handle,
            String displayName,
            String avatarUrl,
            String bio,
            String accountScope) {
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 80) String displayName,
            @Size(max = 500) String bio,
            @Size(max = 2_000) String avatarUrl) {
    }

    public record ChangeHandleRequest(@NotBlank @Size(min = 3, max = 30) String handle) {
    }

    /** @param avatarUrl l'URL rendue par le téléversement ; {@code null} retire la photo */
    public record ChangeAvatarRequest(@Size(max = 2_000) String avatarUrl) {
    }

    public record ChangeScopeRequest(@NotBlank String accountScope) {
    }

    /** Données sensibles : elles n'apparaissent que sur {@code /users/me}. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PhysiologyPayload(
            LocalDate birthDate,
            String biologicalSex,
            @Min(20) @Max(400) Double weightKilograms,
            @Min(50) @Max(280) Double heightCentimeters) {
    }
}
