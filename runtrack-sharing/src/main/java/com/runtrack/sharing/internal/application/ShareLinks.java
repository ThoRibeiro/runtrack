package com.runtrack.sharing.internal.application;

import com.runtrack.course.CourseApi;
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
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Émettre, lister, révoquer un lien — et le résoudre quand quelqu'un s'en sert.
 *
 * <p>Seul le propriétaire d'une course en partage l'accès. Ce n'est pas une évidence : on pourrait
 * imaginer qu'un abonné autorisé repartage ce qu'il a le droit de voir. Ce serait donner à un
 * lecteur le pouvoir de rendre publique une course qui ne l'était pas, et le propriétaire n'en
 * saurait rien.
 *
 * <p>Le module ne connaît de {@code course} que {@link CourseApi} : à aucun moment il ne lit une
 * table de {@code course}, et {@code course} ignore jusqu'à l'existence de {@code sharing} (§5.4).
 */
@Service
public class ShareLinks {

    private final ShareLinkRepository links;
    private final CourseApi courses;
    private final Clock clock;
    private final RandomGenerator random;

    public ShareLinks(ShareLinkRepository links, CourseApi courses, Clock clock,
            RandomGenerator random) {
        this.links = links;
        this.courses = courses;
        this.clock = clock;
        this.random = random;
    }

    /**
     * @return le lien et son jeton en clair — la seule fois où celui-ci existe hors du client
     */
    @Transactional
    public Issued issue(UserId ownerId, ActivityId activityId, Optional<Duration> validFor) {
        requireOwnership(ownerId, activityId);
        Instant now = clock.instant();
        ShareToken token = ShareToken.generate(random);

        ShareLink link = links.save(ShareLink.issued(
                ShareLinkId.generate(clock, random), activityId, ownerId, token, now,
                validFor.map(now::plus)));
        return new Issued(link, token);
    }

    @Transactional(readOnly = true)
    public List<ShareLink> of(UserId ownerId, ActivityId activityId) {
        requireOwnership(ownerId, activityId);
        return links.ofActivity(activityId);
    }

    @Transactional
    public void revoke(UserId ownerId, ShareLinkId id) {
        ShareLink link = links.findById(id).orElseThrow(ShareLinks::notFound);
        if (!link.createdBy().equals(ownerId)) {
            // Introuvable, et non interdit : un 403 confirmerait l'existence d'un lien à qui n'a
            // aucune raison d'en connaître l'identifiant.
            throw notFound();
        }
        links.save(link.revokedAt(clock.instant()));
    }

    /**
     * Le jeton d'un visiteur vers la course qu'il ouvre.
     *
     * <p>Rend vide pour un jeton inconnu, révoqué ou expiré — sans distinguer les trois. Répondre
     * « ce lien a expiré » à un jeton inventé confirmerait qu'un autre jeton, lui, existe.
     */
    @Transactional
    public Optional<ActivityId> resolve(ShareToken token) {
        Instant now = clock.instant();
        return links.findByTokenHash(token.hash())
                .filter(link -> link.isUsableAt(now))
                .map(link -> {
                    links.recordView(link.id(), now);
                    return link.activityId();
                });
    }

    private void requireOwnership(UserId ownerId, ActivityId activityId) {
        UserId owner = courses.ownerOf(activityId).orElseThrow(ShareLinks::notFound);
        if (!owner.equals(ownerId)) {
            throw new ForbiddenException("ACTIVITY_NOT_YOURS", "Seul le propriétaire partage sa course");
        }
    }

    private static NotFoundException notFound() {
        return new NotFoundException("SHARE_LINK_NOT_FOUND", "Lien de partage introuvable");
    }

    /** Le jeton en clair n'accompagne le lien qu'à sa création ; ensuite il n'existe plus nulle part. */
    public record Issued(ShareLink link, ShareToken token) {
    }
}
