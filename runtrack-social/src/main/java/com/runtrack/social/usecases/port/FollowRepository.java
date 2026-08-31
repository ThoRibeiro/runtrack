package com.runtrack.social.usecases.port;

import com.runtrack.shared.id.UserId;
import com.runtrack.social.usecases.model.graph.Follow;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface FollowRepository {

    Optional<Follow> findById(UUID id);

    Optional<Follow> findBetween(UserId followerId, UserId followeeId);

    /** En une requête : c'est la liste de destinataires du fan-out. */
    Set<UserId> acceptedFollowerIds(UserId followeeId);

    Set<UserId> acceptedFolloweeIds(UserId followerId);

    List<Follow> pendingRequestsFor(UserId followeeId);

    Follow save(Follow follow);

    void delete(UUID id);

    /** Rompt les abonnements dans les deux sens : c'est l'effet d'un blocage. */
    void deleteBetween(UserId one, UserId other);
}
