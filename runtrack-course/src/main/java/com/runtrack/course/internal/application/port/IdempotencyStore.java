package com.runtrack.course.internal.application.port;

import com.runtrack.shared.id.ActivityId;
import java.util.Optional;

/**
 * La mémoire des requêtes déjà traitées, pour qu'un renvoi rende la même réponse.
 *
 * <p>Portée : une course. Deux courses peuvent réutiliser la même clé sans se gêner, ce qui
 * évite d'exiger du client une unicité globale qu'il n'a aucun moyen simple de garantir.
 */
public interface IdempotencyStore {

    /** La réponse mémorisée, si cette clé a déjà servi sur cette course. */
    Optional<StoredResponse> find(ActivityId activityId, String key);

    void store(ActivityId activityId, String key, String requestDigest, String responseBody);

    /**
     * @param requestDigest empreinte du corps d'origine, pour repérer une même clé
     *     réutilisée avec un contenu différent — un bug client, pas un rejeu
     */
    record StoredResponse(String requestDigest, String responseBody) {
    }
}
