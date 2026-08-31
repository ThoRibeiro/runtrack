package com.runtrack.sharing.internal.application.port;

import com.runtrack.shared.id.ActivityId;
import com.runtrack.sharing.internal.domain.link.ShareLink;
import com.runtrack.sharing.internal.domain.link.ShareLinkId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Les liens de partage en base. */
public interface ShareLinkRepository {

    ShareLink save(ShareLink link);

    Optional<ShareLink> findByTokenHash(String tokenHash);

    Optional<ShareLink> findById(ShareLinkId id);

    List<ShareLink> ofActivity(ActivityId activityId);

    /**
     * Incrémente le compteur de vues sans relire ni réécrire le lien.
     *
     * <p>C'est un compteur, pas un invariant : deux ouvertures simultanées doivent en compter deux,
     * et un {@code UPDATE ... SET view_count = view_count + 1} le garantit là où un chargement suivi
     * d'une sauvegarde en perdrait une.
     */
    void recordView(ShareLinkId id, Instant at);
}
