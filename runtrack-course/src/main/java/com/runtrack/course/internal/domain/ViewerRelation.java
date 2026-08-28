package com.runtrack.course.internal.domain;

/**
 * Ce qui lie le lecteur au propriétaire de la course, sous forme de faits <em>déjà
 * résolus</em>.
 *
 * <p>C'est la pièce qui permet à {@link ActivityAccessPolicy} de rester du Java pur alors
 * que la règle a besoin de savoir qui suit qui : le domaine décide, la couche application
 * résout. Sans cela, il faudrait un appel à {@code SocialApi} depuis le domaine.
 */
public record ViewerRelation(boolean isOwner, boolean isAcceptedFollower, boolean isBlockedEitherWay) {

    public static final ViewerRelation NONE = new ViewerRelation(false, false, false);

    public static ViewerRelation owner() {
        return new ViewerRelation(true, false, false);
    }

    public static ViewerRelation acceptedFollower() {
        return new ViewerRelation(false, true, false);
    }

    public static ViewerRelation blocked() {
        return new ViewerRelation(false, false, true);
    }
}
