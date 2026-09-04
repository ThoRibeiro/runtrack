package com.runtrack.feed.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/** Les contrats HTTP de {@code feed}. */
public final class FeedDtos {

    private FeedDtos() {
    }

    /**
     * Une ligne de fil, auteur compris.
     *
     * <p>L'auteur est imbriqué plutôt que réduit à un identifiant : sans cela, l'écran ferait une
     * requête par ligne pour aller chercher un nom — le N+1 que le §10 interdit, déplacé côté
     * client. {@code previewPolyline} suit la même logique : la carte de la ligne se dessine
     * avec ce qui arrive dans la page, sans une requête de trace par course.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FeedItem(
            String activityId,
            AuthorDto author,
            String type,
            String title,
            String status,
            double distanceMeters,
            long movingTimeSeconds,
            Instant startedAt,
            Instant endedAt,
            long likeCount,
            long commentCount,
            String previewPolyline) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AuthorDto(String id, String handle, String displayName, String avatarUrl) {
    }

    /** Pagination par curseur : le client renvoie {@code nextCursor} pour la page suivante. */
    public record FeedPage(List<FeedItem> items, Instant nextCursor) {
    }
}
