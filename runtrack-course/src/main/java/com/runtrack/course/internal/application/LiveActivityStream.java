package com.runtrack.course.internal.application;

import com.runtrack.course.internal.application.port.TrackPointRepository;
import com.runtrack.course.internal.domain.activity.Activity;
import com.runtrack.course.internal.domain.live.LiveEvent;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * L'instantané qu'un spectateur reçoit en se branchant sur une course.
 *
 * <p>Sans lui, celui qui arrive à mi-course voit une carte vide jusqu'au prochain point, soit
 * jusqu'à dix secondes d'écran mort — et rien du tout si le coureur est à l'arrêt.
 *
 * <p>La reprise après coupure n'est pas ici : rejouer les entrées manquées est une affaire de
 * transport, pas de métier. Ce service ne connaît que l'état de la course.
 */
@Service
public class LiveActivityStream {

    /**
     * La longueur de l'instantané.
     *
     * <p>Deux cents points, soit une demi-heure à un point toutes les dix secondes : assez pour
     * voir où en est le coureur, pas assez pour qu'une sortie de trois heures fasse voyager dix
     * mille points à chaque connexion. C'est une décision d'affichage et non un réglage
     * d'exploitation, d'où la constante.
     */
    public static final int SNAPSHOT_POINTS = 200;

    private final ActivityQueries queries;
    private final TrackPointRepository points;

    public LiveActivityStream(ActivityQueries queries, TrackPointRepository points) {
        this.queries = queries;
        this.points = points;
    }

    /** L'état, les statistiques, puis la fin du tracé : de quoi peindre l'écran d'un coup. */
    @Transactional(readOnly = true)
    public List<LiveEvent> snapshotOf(Activity activity) {
        var snapshot = new ArrayList<LiveEvent>();
        snapshot.add(new LiveEvent.Status(
                activity.status().getClass().getSimpleName(), activity.status().since()));
        snapshot.add(new LiveEvent.Stats(queries.statsOf(activity)));
        points.findRecent(activity.id(), SNAPSHOT_POINTS)
                .forEach(point -> snapshot.add(LiveEvent.Position.of(point)));
        return List.copyOf(snapshot);
    }
}
