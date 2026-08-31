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

    /**
     * La première page du fil d'un lecteur.
     *
     * <p>Une opération à part de {@link #page}, et non un cas particulier de celle-ci, parce que
     * c'est la seule dont le résultat <em>se répète</em> : elle est ouverte à chaque lancement de
     * l'application, toujours identique, là où une page à curseur est unique et n'a personne
     * d'autre à qui servir. Le lecteur en fait partie parce qu'il désigne <em>de qui</em> est ce
     * fil — deux personnes ne suivent pas les mêmes comptes — et c'est ce qui la rend cachable.
     */
    List<FeedEntry> headOf(com.runtrack.shared.id.UserId reader,
            Collection<com.runtrack.shared.id.UserId> owners, int limit);
}
