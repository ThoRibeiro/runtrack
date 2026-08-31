package com.runtrack.course.usecases.port;

import com.runtrack.course.usecases.model.stats.RunnerTotals;
import com.runtrack.shared.id.UserId;
import java.time.Instant;
import java.util.Optional;

/**
 * Le bilan d'un coureur, agrégé <b>en base</b>.
 *
 * <p>Un port à part de {@link ActivityRepository}, qui rend des agrégats : celui-ci ne rend aucun
 * objet du domaine, seulement des sommes. Les mêler ferait passer une lecture de tableau de bord
 * pour un chargement de courses, avec la tentation de boucler dessus — précisément le N+1 que le
 * §10 interdit, sur la requête la plus lourde de l'application.
 */
public interface RunnerTotalsRepository {

    /**
     * @param since borne inférieure incluse, absente pour « depuis toujours »
     * @return les totaux, jamais {@code null} : un coureur sans course a des totaux à zéro
     */
    RunnerTotals totalsOf(UserId ownerId, Optional<Instant> since);
}
