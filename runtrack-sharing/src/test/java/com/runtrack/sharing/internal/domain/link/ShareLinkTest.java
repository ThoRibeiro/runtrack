package com.runtrack.sharing.internal.domain.link;

import static org.assertj.core.api.Assertions.assertThat;

import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShareLinkTest {

    private static final ActivityId RUN = new ActivityId(UUID.fromString("018f4c1e-0000-7000-8000-0000000000ff"));
    private static final UserId MARIE = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000001"));
    private static final Instant NOON = Instant.parse("2026-08-31T12:00:00Z");

    private static ShareLink linkExpiring(Optional<Instant> expiresAt) {
        return ShareLink.issued(new ShareLinkId(UUID.randomUUID()), RUN, MARIE,
                ShareToken.generate(new Random(1)), NOON, expiresAt);
    }

    @Test
    void aFreshLinkWithoutAnExpiryStaysUsable() {
        assertThat(linkExpiring(Optional.empty()).isUsableAt(NOON.plusSeconds(86_400))).isTrue();
    }

    @Test
    void anExpiredLinkStopsWorkingOnItsOwn() {
        ShareLink link = linkExpiring(Optional.of(NOON.plusSeconds(3_600)));

        assertThat(link.isUsableAt(NOON.plusSeconds(3_599))).isTrue();
        assertThat(link.isUsableAt(NOON.plusSeconds(3_600))).isFalse();
    }

    @Test
    void aRevokedLinkStopsWorkingImmediately() {
        ShareLink revoked = linkExpiring(Optional.empty()).revokedAt(NOON);

        assertThat(revoked.isUsableAt(NOON.plusSeconds(1))).isFalse();
    }

    /** Révoquer deux fois ne déplace pas la date : c'est le premier geste qui a fermé le lien. */
    @Test
    void revokingTwiceKeepsTheFirstMoment() {
        ShareLink revoked = linkExpiring(Optional.empty()).revokedAt(NOON);

        assertThat(revoked.revokedAt(NOON.plusSeconds(60)).revokedAt()).contains(NOON);
    }

    /** Le jeton n'est jamais conservé : ce que porte le lien est son empreinte. */
    @Test
    void theLinkKeepsTheDigestAndNotTheSecret() {
        ShareToken token = ShareToken.generate(new Random(7));
        ShareLink link = ShareLink.issued(new ShareLinkId(UUID.randomUUID()), RUN, MARIE, token,
                NOON, Optional.empty());

        assertThat(link.tokenHash()).isEqualTo(token.hash()).isNotEqualTo(token.value());
    }
}
