package com.runtrack.course.infrastructure.access;

import com.runtrack.course.usecases.port.ViewerRelationResolver;
import com.runtrack.course.usecases.model.access.ViewerRelation;
import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.access.Viewer;
import com.runtrack.shared.id.UserId;
import com.runtrack.social.SocialApi;
import com.runtrack.user.UserApi;
import org.springframework.stereotype.Component;

/**
 * Résout la relation du lecteur en interrogeant {@code user} et {@code social}.
 *
 * <p>Vit dans {@code infra} et non dans le domaine : c'est ce qui permet à
 * {@code ActivityAccessPolicy} de rester du Java pur, testable sans mock. Le domaine reçoit
 * des faits, il ne va pas les chercher.
 *
 * <p>Un compte propriétaire inconnu est traité comme {@code PRIVATE}. Une donnée manquante
 * doit fermer l'accès, jamais l'ouvrir.
 */
@Component
class DefaultViewerRelationResolver implements ViewerRelationResolver {

    private final UserApi users;
    private final SocialApi social;

    DefaultViewerRelationResolver(UserApi users, SocialApi social) {
        this.users = users;
        this.social = social;
    }

    @Override
    public ViewerRelation relationOf(Viewer viewer, UserId ownerId) {
        return viewer.userId()
                .map(readerId -> new ViewerRelation(
                        readerId.equals(ownerId),
                        social.isFollowing(readerId, ownerId),
                        social.isBlockedEitherWay(readerId, ownerId)))
                .orElse(ViewerRelation.NONE);
    }

    @Override
    public AudienceScope accountScopeOf(UserId ownerId) {
        return users.accountScope(ownerId).orElse(AudienceScope.PRIVATE);
    }
}
