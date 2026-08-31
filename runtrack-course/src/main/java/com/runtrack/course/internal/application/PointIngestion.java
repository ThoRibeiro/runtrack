package com.runtrack.course.internal.application;

import com.runtrack.course.internal.application.port.ActivityRepository;
import com.runtrack.course.internal.application.port.ActivityStatsStore;
import com.runtrack.course.internal.application.port.LiveActivityPublisher;
import com.runtrack.course.internal.application.port.TrackPointRepository;
import com.runtrack.course.internal.domain.activity.Activity;
import com.runtrack.course.internal.domain.live.LiveEvent;
import com.runtrack.course.internal.domain.stats.ActivityStats;
import com.runtrack.course.internal.domain.stats.StatsAccumulator;
import com.runtrack.course.internal.domain.track.PointRejection;
import com.runtrack.course.internal.domain.track.TrackPoint;
import com.runtrack.course.internal.domain.track.TrackPointFilter;
import com.runtrack.shared.error.NotFoundException;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * L'ingestion d'un lot de points.
 *
 * <p>L'ordre des opérations est ce qui rend le rejeu inoffensif, et il n'est pas
 * interchangeable :
 * <ol>
 *   <li>les points sont <b>triés</b> par numéro de séquence — un lot rejoué peut arriver
 *       dans le désordre, et l'accumulateur est incrémental ;</li>
 *   <li>chaque point est <b>filtré</b>, en incluant le rejet des séquences déjà
 *       appliquées, <em>avant</em> d'entrer dans l'accumulateur ;</li>
 *   <li>l'accumulateur avance point par point, et son curseur progresse avec lui ;</li>
 *   <li>l'insertion est un lot unique, sur une clé primaire qui rend le doublon
 *       impossible même si tout ce qui précède a été contourné.</li>
 * </ol>
 *
 * <p>Deux filets se recouvrent donc volontairement : le curseur de l'accumulateur et la
 * clé primaire. Le premier protège les statistiques, le second protège la trace, et aucun
 * ne couvre ce que couvre l'autre.
 */
@Service
public class PointIngestion {

    private final ActivityRepository activities;
    private final ActivityStatsStore stats;
    private final TrackPointRepository points;
    private final ActivityQueries queries;
    private final LiveActivityPublisher live;
    private final Clock clock;

    public PointIngestion(ActivityRepository activities, ActivityStatsStore stats,
            TrackPointRepository points, ActivityQueries queries, LiveActivityPublisher live, Clock clock) {
        this.activities = activities;
        this.stats = stats;
        this.points = points;
        this.queries = queries;
        this.live = live;
        this.clock = clock;
    }

    @Transactional
    public IngestionResult ingest(UserId ownerId, ActivityId activityId, List<TrackPoint> batch) {
        Activity activity = activities.findById(activityId)
                .orElseThrow(() -> new NotFoundException("ACTIVITY_NOT_FOUND", "Course introuvable"));
        if (!activity.ownerId().equals(ownerId)) {
            // Comme ailleurs : ne pas confirmer l'existence d'une course qui n'est pas la sienne.
            throw new NotFoundException("ACTIVITY_NOT_FOUND", "Course introuvable");
        }
        activity.requireAcceptingPoints();

        StatsAccumulator accumulator = stats.find(activityId).orElseGet(StatsAccumulator::empty);
        Instant now = clock.instant();

        var accepted = new ArrayList<TrackPoint>(batch.size());
        var rejected = new ArrayList<IngestionResult.Rejected>();
        Optional<TrackPoint> previous = points.findLast(activityId);

        for (TrackPoint raw : batch.stream()
                .sorted(Comparator.comparingInt(TrackPoint::sequenceNumber))
                .toList()) {

            TrackPoint point = corrected(activity, raw);
            var context = new TrackPointFilter.Context(
                    previous, accumulator.lastAppliedSequence(), activity.startedAt(), now, activity.type());

            Optional<PointRejection> refusal = TrackPointFilter.evaluate(point, context);
            if (refusal.isPresent()) {
                rejected.add(new IngestionResult.Rejected(point.sequenceNumber(), refusal.get()));
                continue;
            }
            accumulator = accumulator.apply(point);
            accepted.add(point);
            previous = Optional.of(point);
        }

        if (!accepted.isEmpty()) {
            points.appendAll(activityId, accepted);
            stats.save(activityId, accumulator);
        }

        ActivityStats summary = queries.statsOf(activity);
        if (!accepted.isEmpty()) {
            live.publish(activityId, broadcastOf(accepted, summary));
        }

        return new IngestionResult(
                summary,
                accumulator.lastAppliedSequence(),
                accepted.size(),
                List.copyOf(rejected));
    }

    /**
     * Ce qui part vers les spectateurs : chaque position, puis les statistiques une fois.
     *
     * <p>Les statistiques ne sont pas répétées point par point. Elles ne prennent leur valeur
     * qu'une fois le lot entier appliqué, et en émettre une version intermédiaire par point
     * ferait clignoter l'écran du spectateur sur des états qui n'ont jamais existé.
     */
    private static List<LiveEvent> broadcastOf(List<TrackPoint> accepted, ActivityStats summary) {
        var broadcast = new ArrayList<LiveEvent>(accepted.size() + 1);
        accepted.forEach(point -> broadcast.add(LiveEvent.Position.of(point)));
        broadcast.add(new LiveEvent.Stats(summary));
        return List.copyOf(broadcast);
    }

    /**
     * Ramène l'horodatage du téléphone à l'heure serveur.
     *
     * <p>La dérive a été mesurée une fois au démarrage : l'appliquer ici, et non à la
     * réception de chaque lot, garantit que tous les points d'une course partagent la même
     * référence de temps, même si le téléphone se resynchronise en cours de route.
     */
    private static TrackPoint corrected(Activity activity, TrackPoint raw) {
        return new TrackPoint(
                raw.sequenceNumber(),
                raw.position(),
                raw.elevation(),
                activity.clockSkew().correct(raw.recordedAt()),
                raw.accuracyMeters(),
                raw.heartRate(),
                raw.cadence());
    }
}
