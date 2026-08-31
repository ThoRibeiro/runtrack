package com.runtrack.course.usecases.port;

import com.runtrack.course.usecases.model.access.ActivityAudience;
import com.runtrack.course.usecases.model.access.ViewerRelation;
import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.access.Viewer;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;

/**
 * Résout les faits dont la règle d'accès a besoin : est-ce le propriétaire, un abonné
 * accepté, un compte bloqué, et quelle est la portée du compte.
 *
 * <p>C'est la pièce qui permet à {@code ActivityAccessPolicy} de rester du Java pur. Le
 * domaine décide, l'application résout — sans cette séparation, il faudrait appeler
 * {@code SocialApi} depuis le domaine.
 */
public interface ViewerRelationResolver {

    ViewerRelation relationOf(Viewer viewer, UserId ownerId);

    /** La portée du compte propriétaire, à composer avec celle de la course. */
    AudienceScope accountScopeOf(UserId ownerId);

    default ActivityAudience audienceOf(ActivityId id, UserId ownerId, AudienceScope activityScope) {
        return new ActivityAudience(id, ownerId, activityScope, accountScopeOf(ownerId));
    }
}
