package com.runtrack.feed.usecases.port;

import com.runtrack.feed.usecases.model.entry.FeedEntry;
import com.runtrack.shared.id.ActivityId;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * La projection du fil, en lecture et en écriture.
 *
 * <p>Les compteurs s'ajustent en base ({@code like_count = like_count + 1}) plutôt qu'en mémoire :
 * deux « j'aime » simultanés doivent en compter deux, et un chargement suivi d'une sauvegarde en
 * perdrait un.
 */
public interface FeedProjection {

    void upsert(FeedEntry entry);

    void updateVisibility(ActivityId activityId, String effectiveScope);

    void remove(ActivityId activityId);

    void adjustLikes(ActivityId activityId, int delta);

    void adjustComments(ActivityId activityId, int delta);

    Optional<FeedEntry> find(ActivityId activityId);

    /**
     * Une page du fil, la plus récente d'abord.
     *
     * @param owners les comptes dont on veut voir les courses — abonnements du lecteur, et
     *     lui-même
     * @param before curseur : la date de départ de la dernière course déjà reçue
     */
    List<FeedEntry> page(Collection<com.runtrack.shared.id.UserId> owners, Optional<Instant> before,
            int limit);
}
