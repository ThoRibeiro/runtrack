package com.runtrack.notification.usecases.model.inbox;

import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.id.UserId;
import java.util.Set;

/**
 * Qui est prévenu du démarrage d'une course.
 *
 * <p>Une fonction pure, et c'est délibéré : la règle « une course effectivement privée ne notifie
 * personne » est exactement le genre d'invariant qu'on veut pouvoir vérifier sans base ni
 * conteneur.
 *
 * <p><b>La portée reçue est déjà l'effective.</b> {@code course} l'a composée avec celle du compte
 * avant de publier l'événement (§5.1) : une course publique sur un compte privé arrive donc ici
 * marquée privée, et il n'y a rien à recomposer.
 *
 * <p><b>Les blocages ne sont pas refiltrés ici.</b> Bloquer quelqu'un rompt les abonnements dans
 * les deux sens ; un compte bloqué n'est donc plus dans la liste des abonnés acceptés. Rappeler
 * {@code social} une fois par destinataire pour reposer une question à laquelle le graphe a déjà
 * répondu serait le N+1 que le §10 interdit, sur une condition qui ne peut pas se produire.
 */
public final class NotificationAudience {

    private NotificationAudience() {
    }

    public static Set<UserId> forStartedActivity(AudienceScope effectiveScope,
            Set<UserId> acceptedFollowers) {

        if (effectiveScope == null || acceptedFollowers == null) {
            throw new IllegalArgumentException("Audience indéterminable");
        }
        return effectiveScope == AudienceScope.PRIVATE ? Set.of() : Set.copyOf(acceptedFollowers);
    }
}
