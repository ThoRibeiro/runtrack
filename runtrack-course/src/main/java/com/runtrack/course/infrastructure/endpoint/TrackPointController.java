package com.runtrack.course.infrastructure.endpoint;

import static com.runtrack.course.infrastructure.endpoint.Principals.requireUser;

import com.runtrack.course.infrastructure.dto.PointDtos;
import com.runtrack.shared.access.Viewer;
import com.runtrack.shared.id.ActivityId;
import jakarta.validation.Valid;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * L'ingestion des lots de points.
 *
 * <p>Contrôleur distinct du cycle de vie : c'est le seul endpoint appelé toutes les cinq
 * secondes pendant des heures, et le seul à porter l'idempotence par header.
 *
 * <p>La réponse est rendue en {@code String} déjà sérialisée, et non en DTO. Un rejeu doit
 * rendre <em>la réponse mémorisée</em> ; la repasser par le sérialiseur pour la reconstruire
 * ouvrirait la porte à une réponse qui diverge de celle qu'on a promise.
 */
@RestController
@RequestMapping("/api/v1")
class TrackPointController {

    private final IdempotentIngestion ingestion;

    TrackPointController(IdempotentIngestion ingestion) {
        this.ingestion = ingestion;
    }

    @PostMapping(path = "/activities/{id}/points", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> ingest(
            @AuthenticationPrincipal Viewer viewer,
            @PathVariable String id,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PointDtos.IngestPointsRequest request) {

        String body = ingestion.ingest(
                requireUser(viewer), ActivityId.of(id), request, Optional.ofNullable(idempotencyKey));
        return ResponseEntity.ok(body);
    }
}
