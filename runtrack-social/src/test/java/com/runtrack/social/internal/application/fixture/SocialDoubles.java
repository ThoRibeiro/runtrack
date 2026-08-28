package com.runtrack.social.internal.application.fixture;

import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.id.UserId;
import com.runtrack.social.internal.application.port.BlockRepository;
import com.runtrack.social.internal.application.port.FollowRepository;
import com.runtrack.social.internal.domain.graph.Block;
import com.runtrack.social.internal.domain.graph.Follow;
import com.runtrack.user.NewUser;
import com.runtrack.user.RunnerMass;
import com.runtrack.user.UserApi;
import com.runtrack.user.UserSummary;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Doubles en mémoire des ports de {@code social}. */
public final class SocialDoubles {

    private SocialDoubles() {
    }

    public static final class Follows implements FollowRepository {

        private final Map<UUID, Follow> stored = new LinkedHashMap<>();

        @Override
        public Optional<Follow> findById(UUID id) {
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public Optional<Follow> findBetween(UserId followerId, UserId followeeId) {
            return stored.values().stream()
                    .filter(f -> f.followerId().equals(followerId) && f.followeeId().equals(followeeId))
                    .findFirst();
        }

        @Override
        public Set<UserId> acceptedFollowerIds(UserId followeeId) {
            return stored.values().stream()
                    .filter(Follow::isAccepted)
                    .filter(f -> f.followeeId().equals(followeeId))
                    .map(Follow::followerId)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }

        @Override
        public Set<UserId> acceptedFolloweeIds(UserId followerId) {
            return stored.values().stream()
                    .filter(Follow::isAccepted)
                    .filter(f -> f.followerId().equals(followerId))
                    .map(Follow::followeeId)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }

        @Override
        public List<Follow> pendingRequestsFor(UserId followeeId) {
            return stored.values().stream()
                    .filter(f -> !f.isAccepted() && f.followeeId().equals(followeeId))
                    .toList();
        }

        @Override
        public Follow save(Follow follow) {
            stored.put(follow.id(), follow);
            return follow;
        }

        @Override
        public void delete(UUID id) {
            stored.remove(id);
        }

        @Override
        public void deleteBetween(UserId one, UserId other) {
            stored.values().removeIf(f ->
                    (f.followerId().equals(one) && f.followeeId().equals(other))
                            || (f.followerId().equals(other) && f.followeeId().equals(one)));
        }

        public int size() {
            return stored.size();
        }
    }

    public static final class Blocks implements BlockRepository {

        private final Map<UUID, Block> stored = new LinkedHashMap<>();

        @Override
        public Optional<Block> findBetween(UserId blockerId, UserId blockedId) {
            return stored.values().stream()
                    .filter(b -> b.blockerId().equals(blockerId) && b.blockedId().equals(blockedId))
                    .findFirst();
        }

        @Override
        public boolean existsEitherWay(UserId one, UserId other) {
            return findBetween(one, other).isPresent() || findBetween(other, one).isPresent();
        }

        @Override
        public Set<UserId> blockedBy(UserId blockerId) {
            return stored.values().stream()
                    .filter(b -> b.blockerId().equals(blockerId))
                    .map(Block::blockedId)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }

        @Override
        public Block save(Block block) {
            stored.put(block.id(), block);
            return block;
        }

        @Override
        public void delete(UserId blockerId, UserId blockedId) {
            stored.values().removeIf(b -> b.blockerId().equals(blockerId) && b.blockedId().equals(blockedId));
        }
    }

    /** Ne répond qu'à la question que {@code social} pose réellement : la portée du compte. */
    public static final class Users implements UserApi {

        private final Map<UserId, AudienceScope> scopes = new LinkedHashMap<>();

        public Users with(UserId id, AudienceScope scope) {
            scopes.put(id, scope);
            return this;
        }

        @Override
        public Optional<AudienceScope> accountScope(UserId id) {
            return Optional.ofNullable(scopes.get(id));
        }

        @Override
        public boolean exists(UserId id) {
            return scopes.containsKey(id);
        }

        @Override
        public UserId register(NewUser newUser) {
            throw new UnsupportedOperationException("Hors du périmètre de social");
        }

        @Override
        public void confirmEmail(UserId id) {
            throw new UnsupportedOperationException("Hors du périmètre de social");
        }

        @Override
        public Optional<UserId> idOfEmail(String email) {
            return Optional.empty();
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
        public Optional<RunnerMass> massOf(UserId id) {
            return Optional.empty();
        }
    }
}
