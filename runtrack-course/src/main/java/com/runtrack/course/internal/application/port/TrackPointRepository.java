package com.runtrack.course.internal.application.port;

import com.runtrack.course.internal.domain.track.TrackPoint;
import com.runtrack.shared.id.ActivityId;
import java.util.List;
import java.util.Optional;

/** Le port d'écriture et de lecture des points de trace. */
public interface TrackPointRepository {

    /**
     * Insère un lot en une seule opération.
     *
     * <p>Un aller-retour par point ferait de l'ingestion la première source de latence de
     * l'application : un lot de dix secondes en porte dix, et une course de trois heures en
     * produit plus de dix mille.
     *
     * <p>Les points déjà présents sont ignorés silencieusement : la clé primaire
     * {@code (activity_id, sequence_number)} rend le rejeu inoffensif jusqu'au bout.
     */
    int appendAll(ActivityId activityId, List<TrackPoint> points);

    Optional<TrackPoint> findLast(ActivityId activityId);

    List<TrackPoint> findAll(ActivityId activityId);

    void deleteAll(ActivityId activityId);

    int count(ActivityId activityId);
}
