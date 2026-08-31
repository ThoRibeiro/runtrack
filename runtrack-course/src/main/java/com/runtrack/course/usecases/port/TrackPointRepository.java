package com.runtrack.course.usecases.port;

import com.runtrack.course.usecases.model.track.TrackPoint;
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

    /**
     * Les {@code limit} derniers points, dans l'ordre chronologique.
     *
     * <p>Sert l'instantané du direct : un spectateur qui arrive à mi-course doit voir la fin
     * du tracé, pas une carte vide, et pas non plus les dix mille points d'une sortie longue.
     */
    List<TrackPoint> findRecent(ActivityId activityId, int limit);

    List<TrackPoint> findAll(ActivityId activityId);

    void deleteAll(ActivityId activityId);

    int count(ActivityId activityId);
}
