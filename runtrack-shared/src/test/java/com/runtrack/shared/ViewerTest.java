package com.runtrack.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ViewerTest {

    private static final UserId MARIE = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000001"));
    private static final ActivityId RUN = new ActivityId(UUID.fromString("018f4c1e-0000-7000-8000-0000000000aa"));
    private static final ActivityId OTHER_RUN = new ActivityId(UUID.fromString("018f4c1e-0000-7000-8000-0000000000bb"));

    @Test
    void anAuthenticatedUserCarriesItsIdentity() {
        assertThat(new Viewer.AuthenticatedUser(MARIE).userId()).contains(MARIE);
    }

    @Test
    void aShareLinkHolderIsNotAUser() {
        assertThat(new Viewer.ShareLinkHolder(RUN).userId()).isEmpty();
    }

    @Test
    void anAnonymousViewerIsNotAUser() {
        assertThat(Viewer.Anonymous.INSTANCE.userId()).isEmpty();
    }

    /** Un lien ouvre une course, et elle seule : c'est tout l'intérêt d'y stocker l'id. */
    @Test
    void aShareLinkOpensExactlyOneActivity() {
        Viewer.ShareLinkHolder holder = new Viewer.ShareLinkHolder(RUN);

        assertThat(holder.grantsAccessTo(RUN)).isTrue();
        assertThat(holder.grantsAccessTo(OTHER_RUN)).isFalse();
    }

    @Test
    void refusesToBeBuiltWithoutItsSubject() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Viewer.AuthenticatedUser(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new Viewer.ShareLinkHolder(null));
    }
}
