package com.runtrack.sharing.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;

/** Les contrats HTTP de {@code sharing}. */
public final class ShareDtos {

    private ShareDtos() {
    }

    /** @param validForHours durée de vie ; absente, le lien ne périme pas de lui-même */
    public record CreateShareLinkRequest(@Positive Integer validForHours) {
    }

    /**
     * @param token le jeton en clair, rendu <b>une seule fois</b> : il n'existe ensuite nulle part
     *     ailleurs que chez celui qui l'a reçu
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ShareLinkResponse(
            String id,
            String token,
            String url,
            Instant createdAt,
            Instant expiresAt,
            Instant revokedAt,
            long viewCount) {
    }

    public record ShareLinkListResponse(List<ShareLinkResponse> items) {
    }
}
