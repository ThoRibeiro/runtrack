package com.runtrack.social.internal.application;

import com.runtrack.shared.id.UserId;
import com.runtrack.social.SocialApi;
import com.runtrack.social.internal.application.port.BlockRepository;
import com.runtrack.social.internal.application.port.FollowRepository;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** L'implémentation du contrat public du module. */
@Service
class SocialApiAdapter implements SocialApi {

    private final FollowRepository follows;
    private final BlockRepository blocks;

    SocialApiAdapter(FollowRepository follows, BlockRepository blocks) {
        this.follows = follows;
        this.blocks = blocks;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UserId> acceptedFollowerIds(UserId userId) {
        return follows.acceptedFollowerIds(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UserId> acceptedFolloweeIds(UserId userId) {
        return follows.acceptedFolloweeIds(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isFollowing(UserId followerId, UserId followeeId) {
        return follows.findBetween(followerId, followeeId).filter(f -> f.isAccepted()).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isBlockedEitherWay(UserId one, UserId other) {
        return blocks.existsEitherWay(one, other);
    }
}
