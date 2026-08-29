package com.runtrack.course.internal.application.fixture;

import com.runtrack.course.internal.application.port.ActivityRepository;
import com.runtrack.course.internal.application.port.ActivityStatsStore;
import com.runtrack.course.internal.application.port.ViewerRelationResolver;
import com.runtrack.course.internal.domain.access.ViewerRelation;
import com.runtrack.course.internal.domain.activity.Activity;
import com.runtrack.course.internal.domain.stats.StatsAccumulator;
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
import java.util.Optional;

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
