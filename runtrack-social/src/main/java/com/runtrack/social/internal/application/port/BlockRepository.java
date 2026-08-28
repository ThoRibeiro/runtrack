package com.runtrack.social.internal.application.port;

import com.runtrack.shared.id.UserId;
import com.runtrack.social.internal.domain.graph.Block;
import java.util.Optional;
import java.util.Set;

public interface BlockRepository {

    Optional<Block> findBetween(UserId blockerId, UserId blockedId);

    boolean existsEitherWay(UserId one, UserId other);

    Set<UserId> blockedBy(UserId blockerId);

    Block save(Block block);

    void delete(UserId blockerId, UserId blockedId);
}
