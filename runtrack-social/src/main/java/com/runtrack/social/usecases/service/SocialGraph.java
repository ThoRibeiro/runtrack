package com.runtrack.social.usecases.service;

import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.error.ConflictException;
import com.runtrack.shared.error.ForbiddenException;
import com.runtrack.shared.error.NotFoundException;
import com.runtrack.shared.id.UserId;
import com.runtrack.social.event.FollowAccepted;
import com.runtrack.social.event.FollowDropped;
import com.runtrack.social.event.FollowRequested;
import com.runtrack.social.event.UserBlocked;
import com.runtrack.social.usecases.port.BlockRepository;
import com.runtrack.social.usecases.port.FollowRepository;
import com.runtrack.social.usecases.model.graph.Block;
import com.runtrack.social.usecases.model.graph.Follow;
import com.runtrack.user.UserApi;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Abonnements, demandes et blocages. */
@Service
public class SocialGraph {

    private final FollowRepository follows;
    private final BlockRepository blocks;
    private final UserApi users;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public SocialGraph(FollowRepository follows, BlockRepository blocks, UserApi users,
            ApplicationEventPublisher events, Clock clock) {
        this.follows = follows;
        this.blocks = blocks;
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    /**
     * S'abonner. Un compte public accepte d'emblée, un compte fermé reçoit une demande.
     *
     * <p>Réabonner alors qu'on l'est déjà ne relance rien : l'opération est idempotente,
     * sinon un double clic générerait une seconde demande.
     */
    @Transactional
    public Follow follow(UserId followerId, UserId followeeId) {
        if (followerId.equals(followeeId)) {
            throw new ConflictException("SELF_FOLLOW", "On ne s'abonne pas à soi-même");
        }
        if (blocks.existsEitherWay(followerId, followeeId)) {
            // Sans détailler lequel des deux a bloqué l'autre : ce serait renseigner l'appelant.
            throw new ForbiddenException("BLOCKED", "Cet abonnement n'est pas possible");
        }
        return follows.findBetween(followerId, followeeId).orElseGet(() -> {
            AudienceScope scope = users.accountScope(followeeId).orElseThrow(
                    () -> new NotFoundException("USER_NOT_FOUND", "Profil introuvable : " + followeeId));

            Instant now = clock.instant();
            Follow follow = follows.save(Follow.request(UUID.randomUUID(), followerId, followeeId, scope, now));
            events.publishEvent(follow.isAccepted()
                    ? new FollowAccepted(followerId, followeeId, now)
                    : new FollowRequested(followerId, followeeId, now));
            return follow;
        });
    }

    /**
     * Se désabonner. Publie {@link FollowDropped} : sans événement, le cache des abonnés
     * garderait une liste périmée jusqu'à expiration, et le fan-out notifierait encore
     * quelqu'un qui vient de partir.
     */
    @Transactional
    public void unfollow(UserId followerId, UserId followeeId) {
        follows.findBetween(followerId, followeeId).ifPresent(follow -> {
            follows.delete(follow.id());
            events.publishEvent(new FollowDropped(followerId, followeeId, clock.instant()));
        });
    }

    @Transactional
    public void acceptRequest(UserId followeeId, UUID followId) {
        Follow follow = requireRequest(followeeId, followId);
        Instant now = clock.instant();
        follow.accept(now);
        follows.save(follow);
        events.publishEvent(new FollowAccepted(follow.followerId(), follow.followeeId(), now));
    }

    @Transactional
    public void rejectRequest(UserId followeeId, UUID followId) {
        Follow follow = requireRequest(followeeId, followId);
        if (follow.isAccepted()) {
            throw new ConflictException("FOLLOW_ALREADY_ACCEPTED", "Cette demande est déjà acceptée");
        }
        follows.delete(follow.id());
        events.publishEvent(new FollowDropped(follow.followerId(), follow.followeeId(), clock.instant()));
    }

    /**
     * Bloquer, et rompre les abonnements des deux côtés dans la foulée.
     *
     * <p>Le blocage seul ne suffirait pas : la personne resterait abonnée, continuerait de
     * voir passer les courses dans son fil, et seul le contrôle de lecture la retiendrait.
     * Une seule barrière pour une donnée sensible, c'est une de trop peu.
     */
    @Transactional
    public void block(UserId blockerId, UserId blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new ConflictException("SELF_BLOCK", "On ne se bloque pas soi-même");
        }
        blocks.findBetween(blockerId, blockedId).orElseGet(() -> {
            Instant now = clock.instant();
            Block block = blocks.save(new Block(UUID.randomUUID(), blockerId, blockedId, now));
            follows.deleteBetween(blockerId, blockedId);
            events.publishEvent(new UserBlocked(blockerId, blockedId, now));
            return block;
        });
    }

    /** Débloquer ne restaure aucun abonnement : ils ont été rompus, pas suspendus. */
    @Transactional
    public void unblock(UserId blockerId, UserId blockedId) {
        blocks.delete(blockerId, blockedId);
    }

    @Transactional(readOnly = true)
    public Set<UserId> followers(UserId userId) {
        return follows.acceptedFollowerIds(userId);
    }

    @Transactional(readOnly = true)
    public Set<UserId> followees(UserId userId) {
        return follows.acceptedFolloweeIds(userId);
    }

    @Transactional(readOnly = true)
    public List<Follow> pendingRequests(UserId followeeId) {
        return follows.pendingRequestsFor(followeeId);
    }

    @Transactional(readOnly = true)
    public Set<UserId> blockedBy(UserId blockerId) {
        return blocks.blockedBy(blockerId);
    }

    private Follow requireRequest(UserId followeeId, UUID followId) {
        Follow follow = follows.findById(followId).orElseThrow(
                () -> new NotFoundException("FOLLOW_REQUEST_NOT_FOUND", "Demande introuvable"));
        follow.requireOwnedBy(followeeId);
        return follow;
    }
}
