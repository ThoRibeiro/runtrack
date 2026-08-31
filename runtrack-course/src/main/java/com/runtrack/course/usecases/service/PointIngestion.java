package com.runtrack.course.usecases.service;

import com.runtrack.course.usecases.port.ActivityRepository;
import com.runtrack.course.usecases.port.ActivityStatsStore;
import com.runtrack.course.usecases.port.LiveActivityPublisher;
import com.runtrack.course.usecases.port.TrackPointRepository;
import com.runtrack.course.usecases.model.activity.Activity;
import com.runtrack.course.usecases.model.live.LiveEvent;
import com.runtrack.course.usecases.model.stats.ActivityStats;
import com.runtrack.course.usecases.model.stats.StatsAccumulator;
import com.runtrack.course.usecases.model.track.PointRejection;
import com.runtrack.course.usecases.model.track.TrackPoint;
import com.runtrack.course.usecases.model.track.TrackPointFilter;
import com.runtrack.shared.error.NotFoundException;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
    private final Counter accepted;
    private final Counter rejected;
    private final Timer ingestion;

    public PointIngestion(ActivityRepository activities, ActivityStatsStore stats,
            TrackPointRepository points, ActivityQueries queries, LiveActivityPublisher live,
            Clock clock, MeterRegistry meters) {

        this.activities = activities;
        this.stats = stats;
        this.points = points;
        this.queries = queries;
        this.live = live;
        this.clock = clock;
        this.accepted = Counter.builder("runtrack.points.accepted")
                .description("Points de trace retenus").register(meters);
        this.rejected = Counter.builder("runtrack.points.rejected")
                .description("Points écartés par le filtre du domaine").register(meters);
        // La latence de bout en bout du chemin chaud : c'est elle qui dit si un coureur voit sa
        // trace avancer, et le taux de rejet à côté dit si ce qu'il envoie est exploitable.
        this.ingestion = Timer.builder("runtrack.ingestion")
                .description("Durée d'un lot, de la réception à la publication").register(meters);
    }

    @Transactional
    public IngestionResult ingest(UserId ownerId, ActivityId activityId, List<TrackPoint> batch) {
        Timer.Sample sample = Timer.start();
        try {
            return measured(ownerId, activityId, batch);
        } finally {
            sample.stop(ingestion);
        }
    }

    private IngestionResult measured(UserId ownerId, ActivityId activityId, List<TrackPoint> batch) {
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

        this.accepted.increment(accepted.size());
        this.rejected.increment(rejected.size());

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
