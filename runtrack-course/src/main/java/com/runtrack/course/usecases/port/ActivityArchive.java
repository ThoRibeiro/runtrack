package com.runtrack.course.usecases.port;

import com.runtrack.course.usecases.model.stats.Split;
import com.runtrack.shared.id.ActivityId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** La trace historisée d'une course terminée, et ses tronçons. */
public interface ActivityArchive {

    /**
     * Écrit la trace et ses splits ensemble.
     *
     * <p>Ensemble, parce qu'une course archivée dont les splits manqueraient est un état qu'aucun
     * lecteur ne doit observer : l'écran de fin de course les affiche côte à côte.
     */
    void save(ArchivedTrack track, List<Split> splits);

    Optional<ArchivedTrack> find(ActivityId activityId);

    /**
     * Les vignettes de plusieurs courses, en une requête.
     *
     * <p>En lot, et c'est tout l'intérêt : une liste de vingt courses qui irait chercher sa
     * vignette course par course, c'est le N+1 que le §10 interdit.
     *
     * @return les vignettes trouvées, par identifiant de course ; une course sans trace
     *     historisée n'y figure pas
     */
    java.util.Map<ActivityId, String> previewsOf(java.util.Collection<ActivityId> activityIds);

    List<Split> splitsOf(ActivityId activityId);

    void delete(ActivityId activityId);

    /**
     * Purge les points bruts des courses archivées avant {@code cutoff}.
     *
     * <p>Décision du lot 1 : 90 jours. Au-delà, la polyline, la géométrie, les splits et les
     * statistiques suffisent à tout afficher — les points bruts ne servent plus qu'à recalculer ou
     * à exporter un GPX, et l'export cesse donc d'être possible passé ce délai.
     *
     * @return le nombre de courses dont les points viennent d'être effacés
     */
    int purgePointsArchivedBefore(Instant cutoff, Instant purgedAt, int batchSize);

    /**
     * La trace telle qu'elle est conservée.
     *
     * @param polyline la trace simplifiée, encodée
     * @param previewPolyline la même trace, beaucoup plus grossière : ce qu'une vignette de
     *     liste dessine, sans faire télécharger la trace entière
     * @param pointCount ce qu'il en reste après simplification
     * @param rawPointCount ce qu'elle comptait avant — l'écart entre les deux dit ce qu'on a gagné
     * @param positions les positions retenues, pour construire la géométrie PostGIS
     */
    record ArchivedTrack(
            ActivityId activityId,
            String polyline,
            String previewPolyline,
            int pointCount,
            int rawPointCount,
            List<com.runtrack.shared.measure.GeoPoint> positions,
            Instant frozenAt,
            Optional<Instant> pointsPurgedAt) {
    }
}
