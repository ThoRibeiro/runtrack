package com.runtrack.course.internal.infra.realtime;

import com.runtrack.shared.id.ActivityId;
import java.util.function.Consumer;

/**
 * Ce que le registre demande à la couche Dragonfly : suivre, ou cesser de suivre, une course.
 *
 * <p>Une interface pour une seule implémentation, mais elle porte une vraie frontière : le
 * registre d'émetteurs est de la logique de concurrence pure, testable sous charge en
 * millisecondes, et il n'a aucune raison d'exiger un conteneur pour l'être.
 */
interface LiveStreamWatcher {

    /**
     * Appelé quand cette instance accueille son premier spectateur pour cette course.
     *
     * <p>Le puits est passé en paramètre plutôt que résolu par l'implémentation : sans cela le
     * registre et l'abonnement se référenceraient l'un l'autre, et un cycle de dépendances entre
     * deux beans se paie en {@code @Lazy} dispersés.
     */
    void watch(ActivityId activityId, Consumer<RecordedEvent> sink);

    /** Appelé quand elle vient de perdre le dernier. */
    void unwatch(ActivityId activityId);
}
