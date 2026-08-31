package com.runtrack.course.infrastructure.endpoint;

import static com.runtrack.course.infrastructure.endpoint.Principals.requireUser;

import com.runtrack.course.infrastructure.dto.PointDtos;
import com.runtrack.platform.ratelimit.RateLimitProperties;
import com.runtrack.platform.ratelimit.RateLimiter;
import com.runtrack.shared.error.TooManyRequestsException;
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
 *
 * <p><b>Bridé par course</b> (§9), et non par compte : le quota protège une course d'un client qui
 * boucle, pas un coureur d'en démarrer plusieurs. Le plafond est calculé large — un client normal
 * envoie toutes les cinq à dix secondes, soit une douzaine d'appels par minute, et un rejeu de
 * tampon en ajoute quelques-uns d'un coup après une coupure.
 */
@RestController
@RequestMapping("/api/v1")
class TrackPointController {

    private final IdempotentIngestion ingestion;
    private final RateLimiter rateLimiter;
    private final RateLimitProperties quotas;

    TrackPointController(IdempotentIngestion ingestion, RateLimiter rateLimiter,
            RateLimitProperties quotas) {

        this.ingestion = ingestion;
        this.rateLimiter = rateLimiter;
        this.quotas = quotas;
    }

    @PostMapping(path = "/activities/{id}/points", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> ingest(
            @AuthenticationPrincipal Viewer viewer,
            @PathVariable String id,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PointDtos.IngestPointsRequest request) {

        if (!rateLimiter.tryAcquire("ingest:" + id,
                quotas.ingestBatchesPerActivity(), quotas.ingestWindow())) {
            throw new TooManyRequestsException("TOO_MANY_BATCHES",
                    "Trop d'envois sur cette course, ralentissez la cadence");
        }
        String body = ingestion.ingest(
                requireUser(viewer), ActivityId.of(id), request, Optional.ofNullable(idempotencyKey));
        return ResponseEntity.ok(body);
    }
}
