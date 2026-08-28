package com.runtrack.course.internal.domain.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.id.UserId;
import com.runtrack.shared.access.Viewer;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ActivityAccessPolicyTest {

    private static final ActivityId RUN = new ActivityId(UUID.fromString("018f4c1e-0000-7000-8000-0000000000aa"));
    private static final ActivityId ANOTHER_RUN = new ActivityId(UUID.fromString("018f4c1e-0000-7000-8000-0000000000bb"));
    private static final UserId MARIE = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000001"));
    private static final UserId PAUL = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000002"));

    private static ActivityAudience audience(AudienceScope activityScope, AudienceScope accountScope) {
        return new ActivityAudience(RUN, MARIE, activityScope, accountScope);
    }

    private static ActivityAudience audience(AudienceScope scope) {
        return audience(scope, AudienceScope.PUBLIC);
    }

    private static final Viewer OWNER = new Viewer.AuthenticatedUser(MARIE);
    private static final Viewer OTHER_USER = new Viewer.AuthenticatedUser(PAUL);
    private static final Viewer ANONYMOUS = Viewer.Anonymous.INSTANCE;
    private static final Viewer LINK_HOLDER = new Viewer.ShareLinkHolder(RUN);

    @Nested
    class TheAuthorizationMatrix {

        @ParameterizedTest
        @CsvSource({"PUBLIC", "FOLLOWERS", "PRIVATE"})
        void theOwnerAlwaysSeesTheirOwnRun(AudienceScope scope) {
            assertThat(ActivityAccessPolicy.canView(OWNER, audience(scope), ViewerRelation.owner()))
                    .isEqualTo(AccessDecision.GRANTED);
        }

        @ParameterizedTest
        @CsvSource({"PUBLIC, GRANTED", "FOLLOWERS, GRANTED", "PRIVATE, DENIED_PRIVATE"})
        void anAcceptedFollower(AudienceScope scope, AccessDecision expected) {
            assertThat(ActivityAccessPolicy.canView(
                    OTHER_USER, audience(scope), ViewerRelation.acceptedFollower())).isEqualTo(expected);
        }

        @ParameterizedTest
        @CsvSource({
                "PUBLIC,    GRANTED",
                "FOLLOWERS, DENIED_NOT_A_FOLLOWER",
                "PRIVATE,   DENIED_PRIVATE"})
        void anyOtherUser(AudienceScope scope, AccessDecision expected) {
            assertThat(ActivityAccessPolicy.canView(OTHER_USER, audience(scope), ViewerRelation.NONE))
                    .isEqualTo(expected);
        }

        @ParameterizedTest
        @CsvSource({"PUBLIC", "FOLLOWERS", "PRIVATE"})
        void aValidShareLinkOpensEveryVisibility(AudienceScope scope) {
            assertThat(ActivityAccessPolicy.canView(LINK_HOLDER, audience(scope), ViewerRelation.NONE))
                    .isEqualTo(AccessDecision.GRANTED);
        }

        @ParameterizedTest
        @CsvSource({
                "PUBLIC,    GRANTED",
                "FOLLOWERS, DENIED_ANONYMOUS",
                "PRIVATE,   DENIED_ANONYMOUS"})
        void anAnonymousVisitor(AudienceScope scope, AccessDecision expected) {
            assertThat(ActivityAccessPolicy.canView(ANONYMOUS, audience(scope), ViewerRelation.NONE))
                    .isEqualTo(expected);
        }
    }

    @Nested
    class BlockingBeatsEverything {

        @ParameterizedTest
        @CsvSource({"PUBLIC", "FOLLOWERS", "PRIVATE"})
        void evenOnAPublicRun(AudienceScope scope) {
            assertThat(ActivityAccessPolicy.canView(OTHER_USER, audience(scope), ViewerRelation.blocked()))
                    .isEqualTo(AccessDecision.DENIED_BLOCKED);
        }

        /** Y compris muni d'un lien de partage : c'est le seul cas où le lien ne suffit pas. */
        @Test
        void evenWithAShareLink() {
            assertThat(ActivityAccessPolicy.canView(LINK_HOLDER, audience(AudienceScope.PUBLIC), ViewerRelation.blocked()))
                    .isEqualTo(AccessDecision.DENIED_BLOCKED);
        }

        @Test
        void butNeverAgainstTheOwner() {
            var ownerAndBlocked = new ViewerRelation(true, false, true);

            assertThat(ActivityAccessPolicy.canView(OWNER, audience(AudienceScope.PRIVATE), ownerAndBlocked))
                    .isEqualTo(AccessDecision.GRANTED);
        }
    }

    @Nested
    class VisibilitiesCompose {

        /** Le cas que la spécification laissait ouvert : compte fermé, course ouverte. */
        @Test
        void aPublicRunOnAPrivateAccountIsPrivate() {
            var onAPrivateAccount = audience(AudienceScope.PUBLIC, AudienceScope.PRIVATE);

            assertThat(ActivityAccessPolicy.canView(OTHER_USER, onAPrivateAccount, ViewerRelation.acceptedFollower()))
                    .isEqualTo(AccessDecision.DENIED_PRIVATE);
            assertThat(ActivityAccessPolicy.canView(ANONYMOUS, onAPrivateAccount, ViewerRelation.NONE))
                    .isEqualTo(AccessDecision.DENIED_ANONYMOUS);
        }

        @Test
        void aPublicRunOnAFollowersOnlyAccountNeedsAFollower() {
            var onAFollowersAccount = audience(AudienceScope.PUBLIC, AudienceScope.FOLLOWERS);

            assertThat(ActivityAccessPolicy.canView(OTHER_USER, onAFollowersAccount, ViewerRelation.acceptedFollower()))
                    .isEqualTo(AccessDecision.GRANTED);
            assertThat(ActivityAccessPolicy.canView(OTHER_USER, onAFollowersAccount, ViewerRelation.NONE))
                    .isEqualTo(AccessDecision.DENIED_NOT_A_FOLLOWER);
        }

        /** L'inverse ne s'ouvre pas davantage : c'est toujours le plus fermé qui gagne. */
        @Test
        void aPrivateRunOnAPublicAccountStaysPrivate() {
            assertThat(ActivityAccessPolicy.canView(
                    OTHER_USER, audience(AudienceScope.PRIVATE, AudienceScope.PUBLIC), ViewerRelation.acceptedFollower()))
                    .isEqualTo(AccessDecision.DENIED_PRIVATE);
        }
    }

    /** Un lien ouvre une course précise : présenté sur une autre, il ne vaut rien. */
    @Test
    void aShareLinkDoesNotTravel() {
        var otherActivity = new ActivityAudience(ANOTHER_RUN, MARIE, AudienceScope.PRIVATE, AudienceScope.PUBLIC);

        assertThat(ActivityAccessPolicy.canView(LINK_HOLDER, otherActivity, ViewerRelation.NONE))
                .isEqualTo(AccessDecision.DENIED_PRIVATE);
    }

    @Test
    void aShareLinkForAFollowersOnlyRunElsewhereIsRefusedAsNonFollower() {
        var otherActivity = new ActivityAudience(ANOTHER_RUN, MARIE, AudienceScope.FOLLOWERS, AudienceScope.PUBLIC);

        assertThat(ActivityAccessPolicy.canView(LINK_HOLDER, otherActivity, ViewerRelation.NONE))
                .isEqualTo(AccessDecision.DENIED_NOT_A_FOLLOWER);
    }

    @Test
    void refusesAnIncompleteAudience() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ActivityAudience(
                null, MARIE, AudienceScope.PUBLIC, AudienceScope.PUBLIC));
        assertThatIllegalArgumentException().isThrownBy(() -> new ActivityAudience(
                RUN, null, AudienceScope.PUBLIC, AudienceScope.PUBLIC));
        assertThatIllegalArgumentException().isThrownBy(() -> new ActivityAudience(
                RUN, MARIE, null, AudienceScope.PUBLIC));
        assertThatIllegalArgumentException().isThrownBy(() -> new ActivityAudience(
                RUN, MARIE, AudienceScope.PUBLIC, null));
    }

    @Test
    void reportsWhetherAccessWasGranted() {
        assertThat(AccessDecision.GRANTED.isGranted()).isTrue();
        assertThat(AccessDecision.DENIED_BLOCKED.isGranted()).isFalse();
    }
}
