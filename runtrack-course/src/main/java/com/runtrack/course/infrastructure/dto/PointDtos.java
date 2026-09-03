package com.runtrack.course.infrastructure.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Les contrats HTTP de l'ingestion de points. */
public final class PointDtos {

    /**
     * Le plafond d'un lot.
     *
     * <p>Le client envoie toutes les 5 à 10 secondes, soit une poignée de points ; un lot
     * de mille ne survient qu'au rejeu d'un tampon après une longue coupure réseau. Au-delà,
     * ce n'est plus un rejeu mais un client qui déraille, et une requête sans borne est une
     * requête qui peut saturer la mémoire du serveur.
     */
    public static final int MAX_BATCH_SIZE = 1_000;

    private PointDtos() {
    }

    public record IngestPointsRequest(
            @NotEmpty @Size(max = MAX_BATCH_SIZE) List<@Valid PointDto> points) {
    }

    /**
     * Un point brut, tel que le téléphone l'a capturé.
     *
     * <p>Les bornes de latitude, de longitude et d'altitude ne sont pas répétées ici : elles
     * appartiennent aux objets valeur du domaine, et les dupliquer en annotations ferait
     * exister deux vérités qui divergeraient au premier changement.
     */
    public record PointDto(
            @PositiveOrZero int sequenceNumber,
            double latitude,
            double longitude,
            double elevation,
            @NotNull Instant recordedAt,
            @PositiveOrZero double accuracyMeters,
            Integer heartRate,
            Integer cadence) {
    }

    /**
     * Ce que l'ingestion rend au client.
     *
     * <p>{@code lastAcceptedSequence} lui dit jusqu'où purger son tampon ; {@code rejected}
     * lui dit ce qui a été écarté et pourquoi, afin qu'il distingue le doublon attendu d'un
     * capteur qui déraille.
     */
    public record IngestionResponse(
            ActivityDtos.StatsResponse stats,
            int lastAcceptedSequence,
            int acceptedCount,
            List<RejectedPoint> rejected) {
    }

    public record RejectedPoint(int sequenceNumber, String reason) {
    }
}
