package com.runtrack.course;

import com.runtrack.shared.access.Viewer;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** Point d'entrée unique du module {@code course} pour les autres modules. */
public interface CourseApi {

    Optional<ActivitySummary> summary(ActivityId activityId);

    /** Plusieurs résumés en un appel : le fil affiche des lignes, il ne boucle pas. */
    Map<ActivityId, ActivitySummary> summaries(Collection<ActivityId> activityIds);

    Optional<UserId> ownerOf(ActivityId activityId);

    /**
     * La seule porte d'entrée de l'autorisation de lecture.
     *
     * <p>Appelée par les likes, les commentaires, le partage et le fan-out de
     * notifications. Aucun appelant ne réimplémente la règle, et aucun n'a besoin de
     * connaître la visibilité du compte ni la relation d'abonnement : tout est résolu ici.
     */
    boolean canView(Viewer viewer, ActivityId activityId);
}
