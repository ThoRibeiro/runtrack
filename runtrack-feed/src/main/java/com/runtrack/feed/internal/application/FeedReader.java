package com.runtrack.feed.internal.application;

import com.runtrack.feed.internal.application.port.FeedProjection;
import com.runtrack.feed.internal.domain.entry.FeedEntry;
import com.runtrack.shared.id.UserId;
import com.runtrack.social.SocialApi;
import com.runtrack.user.UserApi;
import com.runtrack.user.UserSummary;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La lecture du fil : les courses de ceux qu'on suit, et les siennes.
 *
 * <p><b>Fan-out à la lecture</b>, décidé au lot 1. Il n'existe pas une ligne par destinataire : un
 * compte très suivi provoquerait des dizaines de milliers d'insertions au démarrage de chaque
 * course. La liste des abonnements vient du cache du §6, et la projection est filtrée dessus.
 *
 * <p>Limite assumée, elle aussi décidée au lot 1 : au-delà de quelques milliers d'abonnements, le
 * filtre devient coûteux. On y reviendra si le cas se présente.
 *
 * <p>Trois requêtes pour une page, jamais davantage : les abonnements, la page de projection, les
 * auteurs. Pas de N+1 — c'est pour cela que les compteurs sont déjà dans la projection.
 */
@Service
public class FeedReader {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final FeedProjection projection;
    private final SocialApi social;
    private final UserApi users;

    public FeedReader(FeedProjection projection, SocialApi social, UserApi users) {
        this.projection = projection;
        this.social = social;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Page read(UserId reader, Optional<Instant> before, Integer limit) {
        // Ses propres courses font partie de son fil : on ne s'abonne pas à soi-même, et un fil
        // qui n'affiche pas ce qu'on vient de courir semble cassé.
        Set<UserId> owners = new LinkedHashSet<>(social.acceptedFolloweeIds(reader));
        owners.add(reader);

        List<FeedEntry> entries = projection.page(owners, before, pageSize(limit));
        Map<UserId, UserSummary> authors = users.summaries(entries.stream()
                .map(FeedEntry::ownerId)
                .collect(Collectors.toSet()));

        return new Page(entries, authors,
                entries.isEmpty() ? null : entries.getLast().startedAt());
    }

    private static int pageSize(Integer requested) {
        return requested == null ? DEFAULT_PAGE_SIZE : Math.clamp(requested, 1, MAX_PAGE_SIZE);
    }

    /** Les lignes et leurs auteurs, résolus en une fois pour toute la page. */
    public record Page(List<FeedEntry> entries, Map<UserId, UserSummary> authors, Instant nextCursor) {
    }
}
