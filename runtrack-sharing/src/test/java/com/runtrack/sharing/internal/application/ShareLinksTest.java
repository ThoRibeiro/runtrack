package com.runtrack.sharing.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.runtrack.course.ActivitySummary;
import com.runtrack.course.CourseApi;
import com.runtrack.shared.access.Viewer;
import com.runtrack.shared.error.ForbiddenException;
import com.runtrack.shared.error.NotFoundException;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import com.runtrack.sharing.internal.application.port.ShareLinkRepository;
import com.runtrack.sharing.internal.domain.link.ShareLink;
import com.runtrack.sharing.internal.domain.link.ShareLinkId;
import com.runtrack.sharing.internal.domain.link.ShareToken;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** L'émission, la révocation et la résolution d'un lien de partage. */
class ShareLinksTest {

    private static final ActivityId RUN = new ActivityId(UUID.fromString("018f4c1e-0000-7000-8000-0000000000ff"));
    private static final UserId MARIE = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000001"));
    private static final UserId PAUL = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000002"));
    private static final Instant NOON = Instant.parse("2026-08-31T12:00:00Z");

    /** Les liens en mémoire, avec la même clé unique que la table : l'empreinte du jeton. */
    private static final class Links implements ShareLinkRepository {

        private final Map<ShareLinkId, ShareLink> stored = new LinkedHashMap<>();

        @Override
        public ShareLink save(ShareLink link) {
            stored.put(link.id(), link);
            return link;
        }

        @Override
        public Optional<ShareLink> findByTokenHash(String tokenHash) {
            return stored.values().stream()
                    .filter(link -> link.tokenHash().equals(tokenHash))
                    .findFirst();
        }

        @Override
        public Optional<ShareLink> findById(ShareLinkId id) {
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public List<ShareLink> ofActivity(ActivityId activityId) {
            return stored.values().stream()
                    .filter(link -> link.activityId().equals(activityId))
                    .toList();
        }

        @Override
        public void recordView(ShareLinkId id, Instant at) {
            views++;
        }

        private int views;
    }

    /** Ne répond qu'à la question que {@code sharing} pose : qui possède cette course. */
    private static final class Courses implements CourseApi {

        private UserId owner = MARIE;

        @Override
        public Optional<ActivitySummary> summary(ActivityId activityId) {
            return Optional.empty();
        }

        @Override
        public Map<ActivityId, ActivitySummary> summaries(Collection<ActivityId> activityIds) {
            return Map.of();
        }

        @Override
        public Optional<UserId> ownerOf(ActivityId activityId) {
            return Optional.ofNullable(owner);
        }

        @Override
        public boolean canView(Viewer viewer, ActivityId activityId) {
            return true;
        }
    }

    private Links links;
    private Courses courses;
    private ShareLinks shareLinks;

    @BeforeEach
    void setUp() {
        links = new Links();
        courses = new Courses();
        shareLinks = new ShareLinks(links, courses, Clock.fixed(NOON, ZoneOffset.UTC), new Random(7));
    }

    @Test
    void theOwnerIssuesALinkAndGetsItsTokenOnce() {
        ShareLinks.Issued issued = shareLinks.issue(MARIE, RUN, Optional.empty());

        assertThat(issued.token().value()).isNotBlank();
        assertThat(issued.link().tokenHash()).isEqualTo(issued.token().hash());
        assertThat(issued.link().expiresAt()).isEmpty();
    }

    @Test
    void aLifetimeIsCountedFromNow() {
        ShareLinks.Issued issued = shareLinks.issue(MARIE, RUN, Optional.of(Duration.ofHours(24)));

        assertThat(issued.link().expiresAt()).contains(NOON.plus(Duration.ofHours(24)));
    }

    /**
     * Seul le propriétaire partage sa course.
     *
     * <p>Laisser un lecteur autorisé repartager reviendrait à lui donner le pouvoir de rendre
     * publique une course qui ne l'était pas, sans que le propriétaire en sache rien.
     */
    @Test
    void aReaderCannotSharesomeoneElsesRun() {
        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> shareLinks.issue(PAUL, RUN, Optional.empty()));
    }

    @Test
    void sharingAnUnknownRunIsNotFound() {
        courses.owner = null;

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> shareLinks.issue(MARIE, RUN, Optional.empty()));
    }

    @Test
    void aValidTokenOpensItsRunAndCountsTheView() {
        ShareToken token = shareLinks.issue(MARIE, RUN, Optional.empty()).token();

        assertThat(shareLinks.resolve(token)).contains(RUN);
        assertThat(links.views).isEqualTo(1);
    }

    /** Inconnu, révoqué, expiré : la même réponse vide pour les trois. */
    @Test
    void anUnknownTokenOpensNothing() {
        assertThat(shareLinks.resolve(new ShareToken("invente"))).isEmpty();
        assertThat(links.views).isZero();
    }

    @Test
    void aRevokedTokenOpensNothing() {
        ShareLinks.Issued issued = shareLinks.issue(MARIE, RUN, Optional.empty());

        shareLinks.revoke(MARIE, issued.link().id());

        assertThat(shareLinks.resolve(issued.token())).isEmpty();
    }

    @Test
    void anExpiredTokenOpensNothing() {
        var expiring = new ShareLinks(links, courses,
                Clock.fixed(NOON.plus(Duration.ofHours(2)), ZoneOffset.UTC), new Random(7));
        ShareToken token = shareLinks.issue(MARIE, RUN, Optional.of(Duration.ofHours(1))).token();

        assertThat(expiring.resolve(token)).isEmpty();
    }

    /** Révoquer le lien d'un autre est introuvable, pas interdit : sinon on confirme son existence. */
    @Test
    void nobodyRevokesSomeoneElsesLink() {
        ShareLinks.Issued issued = shareLinks.issue(MARIE, RUN, Optional.empty());

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> shareLinks.revoke(PAUL, issued.link().id()));
    }

    @Test
    void revokingAnUnknownLinkIsNotFound() {
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> shareLinks.revoke(MARIE, new ShareLinkId(UUID.randomUUID())));
    }

    @Test
    void theOwnerListsTheLinksOfTheirRun() {
        shareLinks.issue(MARIE, RUN, Optional.empty());
        shareLinks.issue(MARIE, RUN, Optional.empty());

        assertThat(shareLinks.of(MARIE, RUN)).hasSize(2);
        assertThatExceptionOfType(ForbiddenException.class).isThrownBy(() -> shareLinks.of(PAUL, RUN));
    }
}
