package com.runtrack.social.infrastructure.repository;

import com.runtrack.shared.id.UserId;
import com.runtrack.social.usecases.port.FollowRepository;
import com.runtrack.social.usecases.model.graph.Follow;
import com.runtrack.social.usecases.model.graph.FollowStatus;
import com.runtrack.social.infrastructure.repository.entity.FollowEntity;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class JpaFollowRepository implements FollowRepository {

    private final SpringDataFollowRepository entities;

    JpaFollowRepository(SpringDataFollowRepository entities) {
        this.entities = entities;
    }

    @Override
    public Optional<Follow> findById(UUID id) {
        return entities.findById(id).map(JpaFollowRepository::toDomain);
    }

    @Override
    public Optional<Follow> findBetween(UserId followerId, UserId followeeId) {
        return entities.findByFollowerIdAndFolloweeId(followerId.value(), followeeId.value())
                .map(JpaFollowRepository::toDomain);
    }

    @Override
    public Set<UserId> acceptedFollowerIds(UserId followeeId) {
        return toUserIds(entities.acceptedFollowerIds(followeeId.value()));
    }

    @Override
    public Set<UserId> acceptedFolloweeIds(UserId followerId) {
        return toUserIds(entities.acceptedFolloweeIds(followerId.value()));
    }

    @Override
    public List<Follow> pendingRequestsFor(UserId followeeId) {
        return entities
                .findAllByFolloweeIdAndStatusOrderByRequestedAtDesc(
                        followeeId.value(), FollowStatus.PENDING.name())
                .stream()
                .map(JpaFollowRepository::toDomain)
                .toList();
    }

    @Override
    public Follow save(Follow follow) {
        var incoming = new FollowEntity(follow.id(), follow.followerId().value(),
                follow.followeeId().value(), follow.status().name(),
                follow.requestedAt(), follow.acceptedAt().orElse(null));
        entities.save(entities.findById(follow.id())
                .map(existing -> {
                    existing.refreshFrom(incoming);
                    return existing;
                })
                .orElse(incoming));
        return follow;
    }

    @Override
    public void delete(UUID id) {
        entities.deleteById(id);
    }

    @Override
    public void deleteBetween(UserId one, UserId other) {
        entities.deleteBetween(one.value(), other.value());
    }

    private static Set<UserId> toUserIds(List<UUID> ids) {
        var result = new LinkedHashSet<UserId>(ids.size());
        ids.forEach(id -> result.add(new UserId(id)));
        return result;
    }

    private static Follow toDomain(FollowEntity entity) {
        return Follow.rehydrate(entity.getId(), new UserId(entity.getFollowerId()),
                new UserId(entity.getFolloweeId()), FollowStatus.valueOf(entity.getStatus()),
                entity.getRequestedAt(), entity.getAcceptedAt());
    }
}
