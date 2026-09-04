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

    /**
     * Une poignée de positions par course, réparties sur toute la trace.
     *
     * <p>Sert la vignette d'une course <b>en cours</b> : elle n'a pas encore de trace figée —
     * le gel arrive à la fin — et une carte de fil sans tracé est un cadre vide. L'échantillon
     * est pris en base, pour ne pas ramener dix mille points afin d'en dessiner soixante.
     *
     * <p>En lot, et pour la même raison que partout ailleurs : une requête par course, sur un
     * fil, c'est le N+1 du §10.
     */
    java.util.Map<ActivityId, List<com.runtrack.shared.measure.GeoPoint>> sample(
            java.util.Collection<ActivityId> activityIds, int maxPointsPerActivity);

    void deleteAll(ActivityId activityId);

    int count(ActivityId activityId);
}
