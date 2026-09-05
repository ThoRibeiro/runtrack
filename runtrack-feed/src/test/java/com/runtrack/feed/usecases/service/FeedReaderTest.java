package com.runtrack.feed.usecases.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.runtrack.feed.usecases.port.FeedProjection;
import com.runtrack.feed.usecases.model.entry.FeedEntry;
import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import com.runtrack.social.SocialApi;
import com.runtrack.user.FederatedProfile;
import com.runtrack.user.NewUser;
import com.runtrack.user.RunnerMass;
import com.runtrack.user.UserApi;
import com.runtrack.user.UserSummary;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** La lecture du fil : qui y apparaît, dans quel ordre, et ce qui n'y apparaît pas. */
class FeedReaderTest {

    private static final UserId MARIE = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000001"));
    private static final UserId PAUL = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000002"));
    private static final UserId LEA = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000003"));
    private static final Instant NOON = Instant.parse("2026-08-31T12:00:00Z");

    /** Reproduit le filtre SQL : les propriétaires demandés, hors privé, du plus récent au plus vieux. */
    private static final class Projection implements FeedProjection {

        private final Map<ActivityId, FeedEntry> stored = new LinkedHashMap<>();

        @Override
        public void upsert(FeedEntry entry) {
            stored.put(entry.activityId(), entry);
        }

        @Override
        public void updateVisibility(ActivityId activityId, String effectiveScope) {
        }

        @Override
        public void remove(ActivityId activityId) {
            stored.remove(activityId);
        }

        @Override
        public void adjustLikes(ActivityId activityId, int delta) {
        }

        @Override
        public void adjustComments(ActivityId activityId, int delta) {
        }

        @Override
        public Optional<FeedEntry> find(ActivityId activityId) {
            return Optional.ofNullable(stored.get(activityId));
        }

        @Override
        public List<FeedEntry> headOf(UserId reader, Collection<UserId> owners, int limit) {
            return page(owners, Optional.empty(), limit);
        }

        @Override
        public List<FeedEntry> page(Collection<UserId> owners, Optional<Instant> before, int limit) {
            return stored.values().stream()
                    .filter(entry -> owners.contains(entry.ownerId()))
                    .filter(FeedEntry::isVisibleToFollowers)
                    .filter(entry -> before.map(entry.startedAt()::isBefore).orElse(true))
                    .sorted(Comparator.comparing(FeedEntry::startedAt).reversed())
                    .limit(limit)
                    .toList();
        }
    }

    private static final class Social implements SocialApi {

        private final Set<UserId> followees = new LinkedHashSet<>();

        @Override
        public Set<UserId> acceptedFollowerIds(UserId userId) {
            return Set.of();
        }

        @Override
        public Set<UserId> acceptedFolloweeIds(UserId userId) {
            return Set.copyOf(followees);
        }

        @Override
        public boolean isFollowing(UserId followerId, UserId followeeId) {
            return followees.contains(followeeId);
        }

        @Override
        public boolean isBlockedEitherWay(UserId one, UserId other) {
            return false;
        }
    }

    /** Rend un résumé pour tout identifiant demandé, et note combien de fois on l'a interrogé. */
    private static final class Users implements UserApi {

        private final List<Collection<UserId>> calls = new ArrayList<>();

        @Override
        public boolean ensureProfile(UserId id, FederatedProfile profile) {
            throw new UnsupportedOperationException("Hors du périmètre de ce double");
        }

        @Override
        public UserId register(NewUser newUser) {
            throw new UnsupportedOperationException("Hors du périmètre de feed");
        }

        @Override
        public void confirmEmail(UserId id) {
            throw new UnsupportedOperationException("Hors du périmètre de feed");
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
            return Optional.of(new UserSummary(id, "coureur", "Coureur", Optional.empty()));
        }

        @Override
        public Map<UserId, UserSummary> summaries(Collection<UserId> ids) {
            calls.add(List.copyOf(ids));
            return ids.stream().collect(Collectors.toMap(id -> id, id -> summary(id).orElseThrow()));
        }

        @Override
        public Optional<AudienceScope> accountScope(UserId id) {
            return Optional.of(AudienceScope.PUBLIC);
        }

        @Override
        public Optional<RunnerMass> massOf(UserId id) {
            return Optional.empty();
        }
    }

    private Projection projection;
    private Social social;
    private Users users;
    private FeedReader feed;

    @BeforeEach
    void setUp() {
        projection = new Projection();
        social = new Social();
        users = new Users();
        feed = new FeedReader(projection, social, users);
    }

    private ActivityId entry(UserId owner, AudienceScope scope, long secondsAfterNoon) {
        var id = new ActivityId(UUID.randomUUID());
        projection.upsert(new FeedEntry(id, owner, "RUN", "Sortie", "Finished", scope,
                1_000, 300, NOON.plusSeconds(secondsAfterNoon), Optional.empty(), 0, 0,
                Optional.of("_p~iF~ps|U")));
        return id;
    }

    @Test
    void showsTheRunsOfThoseYouFollow() {
        social.followees.add(PAUL);
        entry(PAUL, AudienceScope.PUBLIC, 10);

        assertThat(feed.read(MARIE, Optional.empty(), null).entries()).hasSize(1);
    }

    /** On ne s'abonne pas à soi-même, et un fil sans ses propres courses semble cassé. */
    @Test
    void showsYourOwnRunsToo() {
        entry(MARIE, AudienceScope.PUBLIC, 10);

        assertThat(feed.read(MARIE, Optional.empty(), null).entries()).hasSize(1);
    }

    @Test
    void ignoresTheRunsOfStrangers() {
        entry(LEA, AudienceScope.PUBLIC, 10);

        assertThat(feed.read(MARIE, Optional.empty(), null).entries()).isEmpty();
    }

    @Test
    void aPrivateRunNeverAppears() {
        social.followees.add(PAUL);
        entry(PAUL, AudienceScope.PRIVATE, 10);

        assertThat(feed.read(MARIE, Optional.empty(), null).entries()).isEmpty();
    }

    @Test
    void theMostRecentComesFirst() {
        social.followees.add(PAUL);
        entry(PAUL, AudienceScope.PUBLIC, 10);
        entry(PAUL, AudienceScope.PUBLIC, 20);

        FeedReader.Page page = feed.read(MARIE, Optional.empty(), null);

        assertThat(page.entries().getFirst().startedAt()).isEqualTo(NOON.plusSeconds(20));
        assertThat(page.nextCursor()).isEqualTo(NOON.plusSeconds(10));
    }

    @Test
    void paginatesByCursor() {
        social.followees.add(PAUL);
        entry(PAUL, AudienceScope.PUBLIC, 10);
        entry(PAUL, AudienceScope.PUBLIC, 20);

        FeedReader.Page first = feed.read(MARIE, Optional.empty(), 1);
        FeedReader.Page second = feed.read(MARIE, Optional.of(first.nextCursor()), 1);

        assertThat(second.entries()).singleElement()
                .extracting(FeedEntry::startedAt).isEqualTo(NOON.plusSeconds(10));
    }

    /** L'interdiction du N+1 : les auteurs d'une page sont résolus en un seul appel. */
    @Test
    void resolvesEveryAuthorInOneCall() {
        social.followees.add(PAUL);
        social.followees.add(LEA);
        entry(PAUL, AudienceScope.PUBLIC, 10);
        entry(LEA, AudienceScope.PUBLIC, 20);
        entry(MARIE, AudienceScope.PUBLIC, 30);

        FeedReader.Page page = feed.read(MARIE, Optional.empty(), null);

        assertThat(page.entries()).hasSize(3);
        assertThat(users.calls).singleElement().asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.collection(UserId.class))
                .containsExactlyInAnyOrder(MARIE, PAUL, LEA);
    }

    @Test
    void anEmptyFeedHasNoCursor() {
        assertThat(feed.read(MARIE, Optional.empty(), null).nextCursor()).isNull();
    }

    /** Une limite délirante est ramenée dans les bornes plutôt que refusée. */
    @Test
    void anAbsurdPageSizeIsClamped() {
        entry(MARIE, AudienceScope.PUBLIC, 10);

        assertThat(feed.read(MARIE, Optional.empty(), 10_000).entries()).hasSize(1);
        assertThat(feed.read(MARIE, Optional.empty(), -5).entries()).hasSize(1);
    }
}
