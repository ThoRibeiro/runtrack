package com.runtrack.course.internal.application.fixture;

import com.runtrack.course.internal.application.port.ActivityRepository;
import com.runtrack.course.internal.application.port.ActivityStatsStore;
import com.runtrack.course.internal.application.port.ActivityArchive;
import com.runtrack.course.internal.application.port.IdempotencyStore;
import com.runtrack.course.internal.application.port.LiveActivityPublisher;
import com.runtrack.course.internal.application.port.TrackPointRepository;
import com.runtrack.course.internal.application.port.ViewerRelationResolver;
import com.runtrack.course.internal.domain.access.ViewerRelation;
import com.runtrack.course.internal.domain.activity.Activity;
import com.runtrack.course.internal.domain.live.LiveEvent;
import com.runtrack.course.internal.domain.stats.Split;
import com.runtrack.course.internal.domain.stats.StatsAccumulator;
import com.runtrack.course.internal.domain.track.TrackPoint;
import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.access.Viewer;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import com.runtrack.user.NewUser;
import com.runtrack.user.RunnerMass;
import com.runtrack.user.UserApi;
import com.runtrack.user.UserSummary;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

/** Doubles en mémoire des ports de {@code course}. */
public final class CourseDoubles {

    private CourseDoubles() {
    }

    public static final class Activities implements ActivityRepository {

        private final Map<ActivityId, Activity> stored = new LinkedHashMap<>();

        @Override
        public Optional<Activity> findById(ActivityId id) {
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public List<Activity> findAllById(Collection<ActivityId> ids) {
            var found = new ArrayList<Activity>();
            ids.forEach(id -> findById(id).ifPresent(found::add));
            return List.copyOf(found);
        }

        @Override
        public List<Activity> findByOwner(UserId ownerId, Optional<Instant> before, int limit) {
            return stored.values().stream()
                    .filter(a -> a.ownerId().equals(ownerId))
                    .filter(a -> before.map(cursor -> a.startedAt().isBefore(cursor)).orElse(true))
                    .sorted(Comparator.comparing(Activity::startedAt).reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<Activity> findLiveOf(Collection<UserId> ownerIds) {
            return stored.values().stream()
                    .filter(a -> ownerIds.contains(a.ownerId()))
                    .filter(a -> a.status().acceptsPoints())
                    .toList();
        }

        @Override
        public Activity save(Activity activity) {
            stored.put(activity.id(), activity);
            return activity;
        }

        @Override
        public void delete(ActivityId id) {
            stored.remove(id);
        }

        public int size() {
            return stored.size();
        }
    }

    public static final class Stats implements ActivityStatsStore {

        private final Map<ActivityId, StatsAccumulator> stored = new LinkedHashMap<>();

        @Override
        public Optional<StatsAccumulator> find(ActivityId activityId) {
            return Optional.ofNullable(stored.get(activityId));
        }

        @Override
        public void save(ActivityId activityId, StatsAccumulator accumulator) {
            stored.put(activityId, accumulator);
        }

        @Override
        public void delete(ActivityId activityId) {
            stored.remove(activityId);
        }

        public boolean holds(ActivityId activityId) {
            return stored.containsKey(activityId);
        }
    }

    /**
     * Les points en mémoire, indexés par numéro de séquence.
     *
     * <p>La table réelle a pour clé primaire {@code (activity_id, sequence_number)} : la carte
     * reproduit cette contrainte, sans quoi le double laisserait passer les doublons que la
     * base refuse, et le test prouverait le contraire de la production.
     */
    public static final class Points implements TrackPointRepository {

        private final Map<ActivityId, NavigableMap<Integer, TrackPoint>> stored = new LinkedHashMap<>();

        @Override
        public int appendAll(ActivityId activityId, List<TrackPoint> points) {
            NavigableMap<Integer, TrackPoint> track =
                    stored.computeIfAbsent(activityId, id -> new TreeMap<>());
            int before = track.size();
            points.forEach(point -> track.putIfAbsent(point.sequenceNumber(), point));
            return track.size() - before;
        }

        @Override
        public Optional<TrackPoint> findLast(ActivityId activityId) {
            return track(activityId).isEmpty()
                    ? Optional.empty()
                    : Optional.of(stored.get(activityId).lastEntry().getValue());
        }

        @Override
        public List<TrackPoint> findRecent(ActivityId activityId, int limit) {
            List<TrackPoint> all = findAll(activityId);
            return all.subList(Math.max(0, all.size() - limit), all.size());
        }

        @Override
        public List<TrackPoint> findAll(ActivityId activityId) {
            return List.copyOf(track(activityId));
        }

        @Override
        public void deleteAll(ActivityId activityId) {
            stored.remove(activityId);
        }

        @Override
        public int count(ActivityId activityId) {
            return track(activityId).size();
        }

        private Collection<TrackPoint> track(ActivityId activityId) {
            return stored.getOrDefault(activityId, new TreeMap<>()).values();
        }
    }

    /** La mémoire des réponses déjà rendues, avec un compteur d'écritures. */
    public static final class IdempotencyKeys implements IdempotencyStore {

        private final Map<String, StoredResponse> stored = new LinkedHashMap<>();
        private int writes;

        @Override
        public Optional<StoredResponse> find(ActivityId activityId, String key) {
            return Optional.ofNullable(stored.get(activityId + "|" + key));
        }

        @Override
        public void store(ActivityId activityId, String key, String requestDigest, String responseBody) {
            writes++;
            stored.put(activityId + "|" + key, new StoredResponse(requestDigest, responseBody));
        }

        public int writes() {
            return writes;
        }
    }

    /** La trace historisée en mémoire, avec la même clé que la table : la course. */
    public static final class Archive implements ActivityArchive {

        private final Map<ActivityId, ArchivedTrack> tracks = new LinkedHashMap<>();
        private final Map<ActivityId, List<Split>> splits = new LinkedHashMap<>();

        @Override
        public void save(ArchivedTrack track, List<Split> theirSplits) {
            tracks.put(track.activityId(), track);
            splits.put(track.activityId(), List.copyOf(theirSplits));
        }

        @Override
        public Optional<ArchivedTrack> find(ActivityId activityId) {
            return Optional.ofNullable(tracks.get(activityId));
        }

        @Override
        public List<Split> splitsOf(ActivityId activityId) {
            return splits.getOrDefault(activityId, List.of());
        }

        @Override
        public void delete(ActivityId activityId) {
            tracks.remove(activityId);
            splits.remove(activityId);
        }

        @Override
        public int purgePointsArchivedBefore(java.time.Instant cutoff, java.time.Instant purgedAt,
                int batchSize) {

            return 0;
        }
    }

    /** Retient ce qui est parti en direct, pour que les tests puissent le regarder. */
    public static final class LivePublisher implements LiveActivityPublisher {

        private final List<LiveEvent> broadcast = new ArrayList<>();
        private final List<ActivityId> closed = new ArrayList<>();

        @Override
        public void publish(ActivityId activityId, List<LiveEvent> events) {
            broadcast.addAll(events);
        }

        @Override
        public void closeStream(ActivityId activityId) {
            closed.add(activityId);
        }

        public List<LiveEvent> broadcast() {
            return List.copyOf(broadcast);
        }

        public List<ActivityId> closed() {
            return List.copyOf(closed);
        }
    }

    /** Relation et portée pilotées à la main : c'est ce que le test fait varier. */
    public static final class Relations implements ViewerRelationResolver {

        private ViewerRelation relation = ViewerRelation.NONE;
        private AudienceScope accountScope = AudienceScope.PUBLIC;

        public Relations withRelation(ViewerRelation value) {
            this.relation = value;
            return this;
        }

        public Relations withAccountScope(AudienceScope value) {
            this.accountScope = value;
            return this;
        }

        @Override
        public ViewerRelation relationOf(Viewer viewer, UserId ownerId) {
            return viewer.userId().filter(ownerId::equals).isPresent() ? ViewerRelation.owner() : relation;
        }

        @Override
        public AudienceScope accountScopeOf(UserId ownerId) {
            return accountScope;
        }
    }

    /** Ne répond qu'à la seule question que {@code course} pose : la masse du coureur. */
    public static final class Users implements UserApi {

        private RunnerMass mass;

        public Users withMass(double kilograms) {
            this.mass = new RunnerMass(kilograms);
            return this;
        }

        @Override
        public Optional<RunnerMass> massOf(UserId id) {
            return Optional.ofNullable(mass);
        }

        @Override
        public UserId register(NewUser newUser) {
            throw new UnsupportedOperationException("Hors du périmètre de course");
        }

        @Override
        public void confirmEmail(UserId id) {
            throw new UnsupportedOperationException("Hors du périmètre de course");
        }

        @Override
        public Optional<UserId> idOfEmail(String email) {
            return Optional.empty();
        }

        @Override
        public boolean exists(UserId id) {
            return true;
        }

        @Override
        public Optional<UserSummary> summary(UserId id) {
            return Optional.empty();
        }

        @Override
        public Map<UserId, UserSummary> summaries(Collection<UserId> ids) {
            return Map.of();
        }

        @Override
        public Optional<AudienceScope> accountScope(UserId id) {
            return Optional.of(AudienceScope.PUBLIC);
        }
    }
}
