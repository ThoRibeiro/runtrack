package com.runtrack.social.internal.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.error.ForbiddenException;
import com.runtrack.shared.id.UserId;
import com.runtrack.social.SocialApi;
import com.runtrack.social.internal.application.SocialGraph;
import com.runtrack.social.internal.domain.graph.Follow;
import com.runtrack.social.support.SocialIntegrationTest;
import com.runtrack.user.internal.application.UserAccounts;
import com.runtrack.user.internal.domain.profile.Email;
import com.runtrack.user.internal.domain.profile.Handle;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Le graphe social contre une vraie base, avec de vrais comptes.
 *
 * <p>Passe par {@code UserAccounts} pour activer un compte et changer sa portée, ce que le
 * chemin HTTP ne permet pas sans intercepter le courriel de confirmation. C'est le seul
 * moyen de couvrir réellement « compte fermé → demande d'abonnement ».
 */
class SocialGraphIT extends SocialIntegrationTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Autowired
    private SocialGraph graph;

    @Autowired
    private SocialApi api;

    @Autowired
    private UserAccounts accounts;

    private UserId activeUser(AudienceScope scope) {
        String handle = "g" + COUNTER.incrementAndGet() + System.nanoTime() % 100_000;
        UserId id = accounts.register(new Handle(handle), new Email(handle + "@example.com"), "Coureur");
        accounts.verifyEmail(id);
        accounts.changeAccountScope(id, scope);
        return id;
    }

    @Test
    void aPublicAccountAcceptsImmediately() {
        UserId marie = activeUser(AudienceScope.PUBLIC);
        UserId paul = activeUser(AudienceScope.PUBLIC);

        Follow follow = graph.follow(paul, marie);

        assertThat(follow.isAccepted()).isTrue();
        assertThat(api.acceptedFollowerIds(marie)).containsExactly(paul);
        assertThat(api.isFollowing(paul, marie)).isTrue();
    }

    /** Le chemin que le test d'API ne pouvait pas atteindre. */
    @Test
    void aPrivateAccountTurnsTheFollowIntoARequest() {
        UserId marie = activeUser(AudienceScope.PRIVATE);
        UserId paul = activeUser(AudienceScope.PUBLIC);

        Follow follow = graph.follow(paul, marie);

        assertThat(follow.isAccepted()).isFalse();
        assertThat(api.acceptedFollowerIds(marie)).isEmpty();
        assertThat(graph.pendingRequests(marie)).hasSize(1);
    }

    @Test
    void aFollowersOnlyAccountAlsoRequiresApproval() {
        UserId marie = activeUser(AudienceScope.FOLLOWERS);
        UserId paul = activeUser(AudienceScope.PUBLIC);

        assertThat(graph.follow(paul, marie).isAccepted()).isFalse();
    }

    @Test
    void acceptingARequestActivatesTheFollow() {
        UserId marie = activeUser(AudienceScope.PRIVATE);
        UserId paul = activeUser(AudienceScope.PUBLIC);
        Follow request = graph.follow(paul, marie);

        graph.acceptRequest(marie, request.id());

        assertThat(api.acceptedFollowerIds(marie)).containsExactly(paul);
        assertThat(graph.pendingRequests(marie)).isEmpty();
    }

    @Test
    void rejectingARequestLeavesNoTrace() {
        UserId marie = activeUser(AudienceScope.PRIVATE);
        UserId paul = activeUser(AudienceScope.PUBLIC);
        Follow request = graph.follow(paul, marie);

        graph.rejectRequest(marie, request.id());

        assertThat(graph.pendingRequests(marie)).isEmpty();
        assertThat(api.acceptedFollowerIds(marie)).isEmpty();
    }

    /** L'index unique tient là où deux transactions concurrentes verraient chacune un graphe vide. */
    @Test
    void thePairIsUniqueInTheDatabase() {
        UserId marie = activeUser(AudienceScope.PUBLIC);
        UserId paul = activeUser(AudienceScope.PUBLIC);

        Follow first = graph.follow(paul, marie);
        Follow second = graph.follow(paul, marie);

        assertThat(second.id()).isEqualTo(first.id());
    }

    @Test
    void blockingBreaksBothDirectionsInTheDatabase() {
        UserId marie = activeUser(AudienceScope.PUBLIC);
        UserId paul = activeUser(AudienceScope.PUBLIC);
        graph.follow(paul, marie);
        graph.follow(marie, paul);

        graph.block(marie, paul);

        assertThat(api.acceptedFollowerIds(marie)).isEmpty();
        assertThat(api.acceptedFollowerIds(paul)).isEmpty();
        assertThat(api.isBlockedEitherWay(marie, paul)).isTrue();
        assertThat(api.isBlockedEitherWay(paul, marie)).isTrue();
        assertThatExceptionOfType(ForbiddenException.class).isThrownBy(() -> graph.follow(paul, marie));
    }

    @Test
    void followeesAreReadableForTheFeed() {
        UserId marie = activeUser(AudienceScope.PUBLIC);
        UserId zoe = activeUser(AudienceScope.PUBLIC);
        UserId paul = activeUser(AudienceScope.PUBLIC);
        graph.follow(paul, marie);
        graph.follow(paul, zoe);

        assertThat(api.acceptedFolloweeIds(paul)).containsExactlyInAnyOrder(marie, zoe);
    }
}
