package com.runtrack.course.infrastructure.endpoint;

import com.runtrack.course.usecases.service.ResilientPointIngestion;
import com.runtrack.course.usecases.port.IdempotencyStore;
import com.runtrack.course.infrastructure.dto.PointDtos;
import com.runtrack.shared.error.ConflictException;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Le premier niveau d'idempotence : le header {@code Idempotency-Key}.
 *
 * <p>Le second — la déduplication par {@code sequenceNumber} — vit dans le domaine et suffit
 * à ce que rien ne soit compté deux fois. Celui-ci résout un autre problème : rendre au
 * client rejouant son tampon <em>la même réponse</em>, et non une réponse correcte mais
 * différente, où tous ses points apparaîtraient soudain comme des doublons.
 *
 * <p>Ce niveau vit dans l'adaptateur HTTP parce que c'est une réponse HTTP qu'il mémorise.
 * Le mettre dans l'application obligerait celle-ci à connaître la sérialisation, alors que
 * son travail s'arrête à un {@code IngestionResult}.
 */
@Component
class IdempotentIngestion {

    /** La largeur de la colonne : au-delà, ce n'est plus une clé mais un corps de requête. */
    static final int MAX_KEY_LENGTH = 200;

    private final ResilientPointIngestion ingestion;
    private final IdempotencyStore memory;
    private final ObjectMapper json;

    IdempotentIngestion(ResilientPointIngestion ingestion, IdempotencyStore memory, ObjectMapper json) {
        this.ingestion = ingestion;
        this.memory = memory;
        this.json = json;
    }

    /**
     * @param key la clé du client, absente ou vide si elle n'a pas été fournie
     * @return le corps JSON de la réponse, rejoué à l'identique si la clé a déjà servi
     */
    String ingest(UserId ownerId, ActivityId activityId, PointDtos.IngestPointsRequest request,
            Optional<String> key) {

        Optional<String> presented = key.map(String::trim).filter(value -> !value.isEmpty());
        if (presented.isEmpty()) {
            // §4 : clé absente → accepté. La dédup par sequenceNumber protège déjà les stats.
            return execute(ownerId, activityId, request);
        }

        String idempotencyKey = presented.get();
        if (idempotencyKey.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "Idempotency-Key limitée à " + MAX_KEY_LENGTH + " caractères");
        }

        String digest = digestOf(request);
        Optional<IdempotencyStore.StoredResponse> remembered = memory.find(activityId, idempotencyKey);
        if (remembered.isPresent()) {
            if (!remembered.get().requestDigest().equals(digest)) {
                throw new ConflictException("IDEMPOTENCY_KEY_REUSED",
                        "Cette Idempotency-Key a déjà servi pour un autre lot de points");
            }
            return remembered.get().responseBody();
        }

        String body = execute(ownerId, activityId, request);
        // Deux requêtes simultanées portant la même clé peuvent toutes deux arriver ici : la
        // dédup par sequenceNumber rend la seconde ingestion sans effet, et l'écriture est un
        // upsert. Le pire cas est donc une réponse recalculée, jamais une statistique faussée.
        memory.store(activityId, idempotencyKey, digest, body);
        return body;
    }

    private String execute(UserId ownerId, ActivityId activityId, PointDtos.IngestPointsRequest request) {
        return json.writeValueAsString(PointMapper.toResponse(
                ingestion.ingest(ownerId, activityId, PointMapper.toDomain(request.points()))));
    }

    /**
     * L'empreinte du lot, calculée sur le DTO désérialisé et non sur les octets reçus.
     *
     * <p>Deux envois identiques peuvent différer d'un espace ou d'un ordre de champs sans que
     * le client l'ait voulu ; les traiter comme des corps différents transformerait un rejeu
     * légitime en 409. Ce qui doit être comparé, ce sont les points, pas leur mise en forme.
     */
    private String digestOf(PointDtos.IngestPointsRequest request) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(json.writeValueAsString(request).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 est requis par la plateforme Java", impossible);
        }
    }
}
