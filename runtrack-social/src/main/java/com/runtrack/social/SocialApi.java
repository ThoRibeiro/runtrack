package com.runtrack.social;

import com.runtrack.shared.id.UserId;
import java.util.Set;

/** Point d'entrée unique du module {@code social} pour les autres modules. */
public interface SocialApi {

    /**
     * Les abonnés acceptés d'un compte. Rendus en une fois, parce que c'est la liste de
     * destinataires du fan-out de notifications et la base du fil : la parcourir en
     * appelant le module une fois par abonné serait le N+1 que le §10 interdit.
     */
    Set<UserId> acceptedFollowerIds(UserId userId);

    /** Les comptes auxquels l'utilisateur est abonné, pour composer son fil. */
    Set<UserId> acceptedFolloweeIds(UserId userId);

    boolean isFollowing(UserId followerId, UserId followeeId);

    /**
     * Un blocage existe dans un sens <em>ou</em> dans l'autre.
     *
     * <p>C'est la seule question que l'autorisation de lecture ait à poser : elle refuse
     * dans les deux sens, sinon bloquer quelqu'un le laisserait continuer à voir vos
     * courses.
     */
    boolean isBlockedEitherWay(UserId one, UserId other);
}
