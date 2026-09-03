package com.runtrack.course.usecases.service;

import com.runtrack.course.usecases.port.ActivityArchive;
import com.runtrack.course.usecases.port.TrackPointRepository;
import com.runtrack.course.usecases.model.activity.Activity;
import com.runtrack.course.usecases.model.stats.Split;
import com.runtrack.course.usecases.model.stats.SplitCalculator;
import com.runtrack.course.usecases.model.track.PolylineEncoder;
import com.runtrack.course.usecases.model.track.TrackPoint;
import com.runtrack.course.usecases.model.track.TrackSimplifier;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.measure.GeoPoint;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * L'historisation d'une course qui vient de se terminer (§4).
 *
 * <p>Trois choses sont produites, et une quatrième ne l'est pas :
 * <ul>
 *   <li>les <b>splits</b>, calculés sur les points bruts, avec interpolation du franchissement ;</li>
 *   <li>la <b>polyline</b>, simplifiée puis encodée, pour que la carte s'affiche ;</li>
 *   <li>la <b>géométrie</b> PostGIS, pour les recherches spatiales à venir ;</li>
 *   <li>pas de copie des <b>statistiques</b> : l'accumulateur est déjà figé par le fait qu'aucun
 *       point n'arrive plus, et une seconde copie ne pourrait que diverger de la première.</li>
 * </ul>
 *
 * <p><b>Dans la transaction de la fin de course</b>, et non après. Une course terminée dont les
 * splits n'existeraient pas encore est un état qu'aucun lecteur ne doit observer, et l'écran de
 * fin les demande dans la seconde. Le coût est une lecture indexée et un parcours linéaire, payés
 * une fois par course — sans commune mesure avec l'appel réseau que le §7 interdit, lui, d'y
 * mettre.
 */
@Service
public class ActivityArchival {

    private static final Logger LOG = LoggerFactory.getLogger(ActivityArchival.class);

    /**
     * Soixante positions pour une vignette : à cent points de côté, on ne distingue pas
     * mieux, et c'est ce qui rend l'échantillon d'une course en cours quasi gratuit.
     */
    private static final int PREVIEW_SAMPLE_POINTS = 60;

    private final TrackPointRepository points;
    private final ActivityArchive archive;
    private final Clock clock;

    public ActivityArchival(TrackPointRepository points, ActivityArchive archive, Clock clock) {
        this.points = points;
        this.archive = archive;
        this.clock = clock;
    }

    @Transactional
    public void freeze(Activity activity) {
        List<TrackPoint> track = points.findAll(activity.id());
        if (track.isEmpty()) {
            // Une course arrêtée sans le moindre point n'a rien à historiser, et écrire une trace
            // vide obligerait chaque lecteur à distinguer « pas encore » de « rien ».
            LOG.debug("Course {} terminée sans point : rien à historiser", activity.id());
            return;
        }

        List<TrackPoint> displayed = TrackSimplifier.simplify(track, TrackSimplifier.TOLERANCE_METERS);
        List<GeoPoint> positions = displayed.stream().map(TrackPoint::position).toList();

        // La vignette part de la trace déjà simplifiée : simplifier une seconde fois, plus
        // grossièrement, coûte quelques centaines de points au lieu de quelques milliers.
        List<GeoPoint> preview = TrackSimplifier
                .simplify(displayed, TrackSimplifier.PREVIEW_TOLERANCE_METERS)
                .stream().map(TrackPoint::position).toList();

        archive.save(
                new ActivityArchive.ArchivedTrack(
                        activity.id(),
                        PolylineEncoder.encode(positions),
                        PolylineEncoder.encode(preview),
                        displayed.size(),
                        track.size(),
                        positions,
                        clock.instant(),
                        Optional.empty()),
                SplitCalculator.byKilometer(track));

        LOG.info("Course {} historisée : {} points ramenés à {}",
                activity.id(), track.size(), displayed.size());
    }

    /**
     * Efface tout ce qu'une course a laissé derrière elle.
     *
     * <p>Appelé à la suppression : sans cela, les points bruts et la trace historisée survivraient
     * à la course elle-même — et le travail de purge, qui ne regarde que ce qui est archivé, ne
     * repasserait jamais dessus.
     */
    @Transactional
    public void purge(ActivityId activityId) {
        points.deleteAll(activityId);
        archive.delete(activityId);
    }

    @Transactional(readOnly = true)
    public Optional<ActivityArchive.ArchivedTrack> trackOf(ActivityId activityId) {
        return archive.find(activityId);
    }

    /**
     * Les vignettes de trace d'une liste de courses, en une requête.
     *
     * <p>Une carte de fil dessine le parcours ; aller chercher la trace course par course
     * ferait vingt requêtes pour une page, ce que le §10 appelle par son nom : N+1.
     */
    @Transactional(readOnly = true)
    public java.util.Map<ActivityId, String> previewsOf(java.util.Collection<ActivityId> ids) {
        java.util.Map<ActivityId, String> previews =
                new java.util.LinkedHashMap<>(archive.previewsOf(ids));

        // Ce qui n'est pas historisé est soit une course qui court encore, soit une
        // course sans trace. Pour la première, la vignette se prend sur le vif : un
        // échantillon de sa trace en cours, sinon sa carte reste un cadre vide tant
        // qu'elle n'est pas terminée.
        List<ActivityId> running = ids.stream().filter(id -> !previews.containsKey(id)).toList();
        if (!running.isEmpty()) {
            points.sample(running, PREVIEW_SAMPLE_POINTS).forEach((id, positions) -> {
                if (positions.size() >= 2) {
                    previews.put(id, PolylineEncoder.encode(positions));
                }
            });
        }
        return java.util.Map.copyOf(previews);
    }

    @Transactional(readOnly = true)
    public List<Split> splitsOf(ActivityId activityId) {
        return archive.splitsOf(activityId);
    }
}
