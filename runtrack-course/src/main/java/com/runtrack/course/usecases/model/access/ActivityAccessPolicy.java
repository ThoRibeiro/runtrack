package com.runtrack.course.usecases.model.access;

import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.access.Viewer;

/**
 * Qui a le droit de voir quelle course. Une règle, un seul endroit.
 *
 * <p>Appelée par tous les points d'entrée — REST, SSE, likes, commentaires, fan-out de
 * notifications — sans qu'aucun ne la réimplémente.
 *
 * <p>Du Java pur : elle n'appelle ni {@code SocialApi} ni {@code UserApi}. Les faits dont
 * elle a besoin lui arrivent résolus dans {@link ViewerRelation}, ce qui la rend testable
 * de manière exhaustive sans le moindre mock.
 */
public final class ActivityAccessPolicy {

    private ActivityAccessPolicy() {
    }

    public static AccessDecision canView(Viewer viewer, ActivityAudience activity, ViewerRelation relation) {
        if (relation.isOwner()) {
            return AccessDecision.GRANTED;
        }
        // Avant le lien de partage, délibérément : un blocage prime sur tout le reste.
        if (relation.isBlockedEitherWay()) {
            return AccessDecision.DENIED_BLOCKED;
        }
        if (holdsLinkFor(viewer, activity)) {
            return AccessDecision.GRANTED;
        }

        AudienceScope scope = activity.effectiveScope();
        if (scope == AudienceScope.PUBLIC) {
            return AccessDecision.GRANTED;
        }
        return switch (viewer) {
            case Viewer.Anonymous ignored -> AccessDecision.DENIED_ANONYMOUS;
            case Viewer.ShareLinkHolder ignored -> deniedBy(scope);
            case Viewer.AuthenticatedUser ignored ->
                    scope == AudienceScope.FOLLOWERS && relation.isAcceptedFollower()
                            ? AccessDecision.GRANTED
                            : deniedBy(scope);
        };
    }

    private static boolean holdsLinkFor(Viewer viewer, ActivityAudience activity) {
        return viewer instanceof Viewer.ShareLinkHolder holder && holder.grantsAccessTo(activity.id());
    }

    private static AccessDecision deniedBy(AudienceScope scope) {
        return scope == AudienceScope.FOLLOWERS
                ? AccessDecision.DENIED_NOT_A_FOLLOWER
                : AccessDecision.DENIED_PRIVATE;
    }
}
