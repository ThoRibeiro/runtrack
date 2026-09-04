package com.runtrack.course.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Les contrats HTTP de {@code course}. */
public final class ActivityDtos {

    private ActivityDtos() {
    }

    public record StartActivityRequest(
            @NotBlank String type,
            @NotBlank @Size(max = 120) String title,
            @Size(max = 2_000) String description,
            @NotBlank String visibility,
            /** L'heure du téléphone, pour mesurer sa dérive une fois pour toute la course. */
            Instant deviceTime) {
    }

    public record UpdateActivityRequest(
            @NotBlank @Size(max = 120) String title,
            @Size(max = 2_000) String description) {
    }

    public record ChangeVisibilityRequest(@NotBlank String visibility) {
    }

    /**
     * @param author le coureur, imbriqué comme dans le fil — un écran qui n'a qu'un
     *     {@code ownerId} ne peut afficher ni son nom ni sa photo, et aucune route ne résout un
     *     identifiant en profil. Absent des réponses du direct, qui ne portent que des chiffres.
     * @param previewPolyline la vignette de la trace, très simplifiée — de quoi dessiner le
     *     parcours sur une carte de liste sans télécharger la trace entière. Absente tant que
     *     la course n'est pas historisée, et sur celles gelées avant son introduction.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ActivityResponse(
            String id,
            String ownerId,
            AuthorDto author,
            String type,
            String title,
            String description,
            String visibility,
            String status,
            Instant startedAt,
            Instant endedAt,
            StatsResponse stats,
            String previewPolyline) {
    }

    /** De quoi nommer et illustrer un coureur, et rien de plus : la copie de {@code UserSummary}. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AuthorDto(String id, String handle, String displayName, String avatarUrl) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StatsResponse(
            double distanceMeters,
            long elapsedSeconds,
            long movingTimeSeconds,
            Long averagePaceSecondsPerKm,
            Long currentPaceSecondsPerKm,
            double elevationGain,
            double elevationLoss,
            Double minAltitude,
            Double maxAltitude,
            Double averageHeartRate,
            Integer maxHeartRate,
            Integer estimatedCalories) {
    }

    /** Pagination par curseur : le client renvoie {@code nextCursor} pour la page suivante. */
    public record ActivityPage(List<ActivityResponse> items, Instant nextCursor) {
    }

    /**
     * La trace historisée.
     *
     * @param polyline la trace simplifiée au format <i>encoded polyline</i> de Google — c'est ce
     *     que la carte dessine, et ce qui pèse quelques kilo-octets au lieu de dizaines
     * @param pointCount ce qu'il en reste après simplification
     * @param rawPointCount ce qu'elle comptait avant ; l'écart dit ce que la simplification a gagné
     * @param pointsPurgedAt renseignée quand les points bruts ont expiré : passé ce moment, la
     *     trace reste affichable mais l'export n'est plus possible
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TrackResponse(
            String polyline,
            int pointCount,
            int rawPointCount,
            Instant frozenAt,
            Instant pointsPurgedAt) {
    }

    /** @param paceSecondsPerKm absente si le tronçon n'a pas duré : il n'y a alors pas d'allure */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SplitResponse(
            int kilometerIndex,
            double distanceMeters,
            long timeSeconds,
            Long paceSecondsPerKm,
            double elevationGain,
            Double averageHeartRate,
            boolean complete) {
    }

    public record SplitsResponse(List<SplitResponse> items) {
    }

    /**
     * Le bilan d'un coureur sur une période.
     *
     * @param period la période demandée, renvoyée telle quelle : l'écran affiche « ce mois-ci »
     *     à partir de ce qu'il a obtenu, pas de ce qu'il croit avoir demandé
     * @param since la borne calendaire réellement appliquée, absente pour « depuis toujours »
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RunnerTotalsResponse(
            String period,
            Instant since,
            long activityCount,
            double distanceMeters,
            long movingTimeSeconds,
            double elevationGain,
            List<TotalsByType> byType) {
    }

    public record TotalsByType(
            String type, long activityCount, double distanceMeters, long movingTimeSeconds) {
    }
}
