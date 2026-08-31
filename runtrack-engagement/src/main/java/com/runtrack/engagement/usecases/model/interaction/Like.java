package com.runtrack.engagement.usecases.model.interaction;

import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import java.time.Instant;

/**
 * Un « j'aime », au plus un par personne et par course.
 *
 * <p>L'unicité n'est pas un champ mais la clé : {@code (activity_id, user_id)} est la clé primaire,
 * de sorte que deux clics simultanés ne peuvent pas produire deux lignes — ce qu'un contrôle
 * préalable en mémoire ne garantirait pas.
 */
public record Like(ActivityId activityId, UserId userId, Instant at) {

    public Like {
        if (activityId == null || userId == null || at == null) {
            throw new IllegalArgumentException("« J'aime » incomplet");
        }
    }
}
