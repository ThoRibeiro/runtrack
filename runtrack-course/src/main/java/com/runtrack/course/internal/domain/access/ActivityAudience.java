package com.runtrack.course.internal.domain.access;

import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.id.UserId;

/**
 * Les seules données d'une course qui entrent dans la décision de visibilité.
 *
 * <p>Les deux portées se composent : la plus fermée gagne. Une course publique sur un
 * compte privé est privée.
 */
public record ActivityAudience(
        ActivityId id,
        UserId ownerId,
        AudienceScope activityScope,
        AudienceScope ownerAccountScope) {

    public ActivityAudience {
        if (id == null || ownerId == null || activityScope == null || ownerAccountScope == null) {
            throw new IllegalArgumentException("ActivityAudience incomplète");
        }
    }

    /**
     * La portée réellement applicable. {@code mostRestrictive} étant commutative,
     * intervertir les deux niveaux ne change rien : l'erreur n'a pas de conséquence.
     */
    public AudienceScope effectiveScope() {
        return activityScope.mostRestrictive(ownerAccountScope);
    }
}
