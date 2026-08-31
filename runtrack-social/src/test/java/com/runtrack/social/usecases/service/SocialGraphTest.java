package com.runtrack.social.usecases.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.error.ConflictException;
import com.runtrack.shared.error.ForbiddenException;
import com.runtrack.shared.error.NotFoundException;
import com.runtrack.shared.id.UserId;
import com.runtrack.social.event.FollowAccepted;
import com.runtrack.social.event.FollowRequested;
import com.runtrack.social.event.UserBlocked;
import com.runtrack.social.usecases.fixture.SocialDoubles;
import com.runtrack.social.usecases.model.graph.Follow;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class SocialGraphTest {

    private static final UserId MARIE = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000001"));
    private static final UserId PAUL = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000002"));
    private static final UserId ZOE = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000003"));
    private static final Instant NOW = Instant.parse("2026-08-29T08:00:00Z");

    private SocialDoubles.Follows follows;
    private SocialDoubles.Blocks blocks;
    private SocialDoubles.Users users;
    private List<Object> published;
    private SocialGraph graph;
    private SocialApiAdapter api;

    @BeforeEach
    void setUp() {
        follows = new SocialDoubles.Follows();
        blocks = new SocialDoubles.Blocks();
        users = new SocialDoubles.Users()
                .with(MARIE, AudienceScope.PUBLIC)
                .with(PAUL, AudienceScope.PRIVATE)
                .with(ZOE, AudienceScope.PUBLIC);
        published = new ArrayList<>();
        ApplicationEventPublisher publisher = published::add;
        graph = new SocialGraph(follows, blocks, users, publisher, Clock.fixed(NOW, ZoneOffset.UTC));
        api = new SocialApiAdapter(follows, blocks);
    }

    @Nested
    class Following {

        @Test
        void aPublicAccountAcceptsAtOnce() {
            Follow follow = graph.follow(PAUL, MARIE);

            assertThat(follow.isAccepted()).isTrue();
            assertThat(graph.followers(MARIE)).containsExactly(PAUL);
            assertThat(published).singleElement().isInstanceOf(FollowAccepted.class);
        }

        @Test
        void aPrivateAccountReceivesARequest() {
            Follow follow = graph.follow(MARIE, PAUL);

            assertThat(follow.isAccepted()).isFalse();
            assertThat(graph.followers(PAUL)).isEmpty();
            assertThat(graph.pendingRequests(PAUL)).hasSize(1);
            assertThat(published).singleElement().isInstanceOf(FollowRequested.class);
        }

        /** Un double clic ne doit pas produire une seconde demande. */
        @Test
        void followingTwiceChangesNothing() {
            graph.follow(MARIE, PAUL);
            published.clear();

            graph.follow(MARIE, PAUL);

            assertThat(follows.size()).isEqualTo(1);
            assertThat(published).isEmpty();
        }

        @Test
        void unfollowingRemovesTheLink() {
            graph.follow(PAUL, MARIE);

            graph.unfollow(PAUL, MARIE);

            assertThat(graph.followers(MARIE)).isEmpty();
        }

        @Test
        void unfollowingSomeoneNeverFollowedIsHarmless() {
            graph.unfollow(PAUL, MARIE);

            assertThat(follows.size()).isZero();
        }

        @Test
        void nobodyFollowsThemselves() {
            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> graph.follow(MARIE, MARIE))
                    .extracting(ConflictException::code)
                    .isEqualTo("SELF_FOLLOW");
        }

        @Test
        void anUnknownAccountCannotBeFollowed() {
            UserId ghost = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-0000000000ff"));

            assertThatExceptionOfType(NotFoundException.class)
                    .isThrownBy(() -> graph.follow(MARIE, ghost));
        }
    }

    @Nested
    class HandlingRequests {

        @Test
        void theFolloweeAcceptsAndTheLinkBecomesActive() {
            UUID requestId = graph.follow(MARIE, PAUL).id();
            published.clear();

            graph.acceptRequest(PAUL, requestId);

            assertThat(graph.followers(PAUL)).containsExactly(MARIE);
            assertThat(graph.pendingRequests(PAUL)).isEmpty();
            assertThat(published).singleElement().isInstanceOf(FollowAccepted.class);
        }

        @Test
        void rejectingDropsTheRequest() {
            UUID requestId = graph.follow(MARIE, PAUL).id();

            graph.rejectRequest(PAUL, requestId);

            assertThat(graph.pendingRequests(PAUL)).isEmpty();
            assertThat(graph.followers(PAUL)).isEmpty();
        }

        /** Personne ne dispose de la liste d'abonnés d'autrui. */
        @Test
        void aThirdPartyCannotAnswerTheRequest() {
            UUID requestId = graph.follow(MARIE, PAUL).id();

            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> graph.acceptRequest(ZOE, requestId))
                    .extracting(ConflictException::code)
                    .isEqualTo("NOT_YOUR_REQUEST");
        }

        @Test
        void anAlreadyAcceptedRequestCannotBeRejected() {
            UUID requestId = graph.follow(PAUL, MARIE).id();

            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> graph.rejectRequest(MARIE, requestId))
                    .extracting(ConflictException::code)
                    .isEqualTo("FOLLOW_ALREADY_ACCEPTED");
        }

        @Test
        void anUnknownRequestIsNotFound() {
            assertThatExceptionOfType(NotFoundException.class)
                    .isThrownBy(() -> graph.acceptRequest(PAUL, UUID.randomUUID()));
        }
    }

    @Nested
    class Blocking {

        /**
         * Le point qui compte : bloquer rompt les abonnements des deux côtés. Sans cela, la
         * personne bloquée resterait abonnée et le contrôle de lecture serait la seule
         * barrière — une de trop peu pour une donnée sensible.
         */
        @Test
        void breaksFollowsInBothDirections() {
            graph.follow(PAUL, MARIE);
            graph.follow(MARIE, ZOE);
            users.with(PAUL, AudienceScope.PUBLIC);
            graph.follow(MARIE, PAUL);
            published.clear();

            graph.block(MARIE, PAUL);

            assertThat(graph.followers(MARIE)).isEmpty();
            assertThat(graph.followees(MARIE)).containsExactly(ZOE);
            assertThat(published).singleElement().isInstanceOf(UserBlocked.class);
        }

        @Test
        void aBlockedPairCannotFollowEitherWay() {
            graph.block(MARIE, PAUL);

            assertThatExceptionOfType(ForbiddenException.class)
                    .isThrownBy(() -> graph.follow(PAUL, MARIE))
                    .extracting(ForbiddenException::code)
                    .isEqualTo("BLOCKED");
            assertThatExceptionOfType(ForbiddenException.class)
                    .isThrownBy(() -> graph.follow(MARIE, PAUL));
        }

        @Test
        void blockingTwiceChangesNothing() {
            graph.block(MARIE, PAUL);
            published.clear();

            graph.block(MARIE, PAUL);

            assertThat(published).isEmpty();
            assertThat(graph.blockedBy(MARIE)).containsExactly(PAUL);
        }

        @Test
        void nobodyBlocksThemselves() {
            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> graph.block(MARIE, MARIE))
                    .extracting(ConflictException::code)
                    .isEqualTo("SELF_BLOCK");
        }

        /** Débloquer ne restaure rien : les abonnements ont été rompus, pas suspendus. */
        @Test
        void unblockingDoesNotRestoreTheFollow() {
            graph.follow(PAUL, MARIE);
            graph.block(MARIE, PAUL);

            graph.unblock(MARIE, PAUL);

            assertThat(graph.followers(MARIE)).isEmpty();
            assertThat(api.isBlockedEitherWay(MARIE, PAUL)).isFalse();
        }
    }

    @Nested
    class TheContractForOtherModules {

        @Test
        void exposesAcceptedFollowersAndFollowees() {
            graph.follow(PAUL, MARIE);
            graph.follow(ZOE, MARIE);
            graph.follow(MARIE, PAUL);

            assertThat(api.acceptedFollowerIds(MARIE)).containsExactlyInAnyOrder(PAUL, ZOE);
            assertThat(api.acceptedFolloweeIds(PAUL)).containsExactly(MARIE);
        }

        /** Une demande en attente n'est pas un abonnement. */
        @Test
        void aPendingRequestIsNotAFollow() {
            graph.follow(MARIE, PAUL);

            assertThat(api.isFollowing(MARIE, PAUL)).isFalse();
            assertThat(api.acceptedFollowerIds(PAUL)).isEmpty();
        }

        @Test
        void reportsAnAcceptedFollow() {
            graph.follow(PAUL, MARIE);

            assertThat(api.isFollowing(PAUL, MARIE)).isTrue();
            assertThat(api.isFollowing(MARIE, PAUL)).isFalse();
        }

        /** L'autorisation de lecture refuse dans les deux sens : la question l'est aussi. */
        @Test
        void blockingIsSymmetricForCallers() {
            graph.block(MARIE, PAUL);

            assertThat(api.isBlockedEitherWay(MARIE, PAUL)).isTrue();
            assertThat(api.isBlockedEitherWay(PAUL, MARIE)).isTrue();
            assertThat(api.isBlockedEitherWay(MARIE, ZOE)).isFalse();
        }
    }
}
