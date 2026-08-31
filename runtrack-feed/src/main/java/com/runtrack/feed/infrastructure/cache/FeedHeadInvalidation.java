package com.runtrack.feed.infrastructure.cache;

import com.runtrack.course.event.ActivityDeleted;
import com.runtrack.course.event.ActivityDiscarded;
import com.runtrack.course.event.ActivityFinished;
import com.runtrack.course.event.ActivityStarted;
import com.runtrack.course.event.ActivityVisibilityChanged;
import com.runtrack.feed.usecases.port.FeedProjection;
import com.runtrack.platform.cache.CacheGateway;
import com.runtrack.platform.cache.CacheKey;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Purge la tête de fil du <b>propriétaire</b> quand sa propre course change.
 *
 * <p>Du propriétaire, et de lui seul : c'est ce qui distingue un compromis d'un bug. Purger aussi
 * les abonnés demanderait, à chaque course démarrée, de parcourir tous les abonnés du coureur —
 * le fan-out à l'écriture que la décision du lot 1 a rejeté, réintroduit par la porte du cache, et
 * qui coûterait des dizaines de milliers d'évictions sur un compte très suivi.
 *
 * <p>Pour les abonnés, l'expiration de trente secondes fait office d'invalidation, et le retard
 * ne se voit pas : ils sont déjà prévenus par une notification et par le direct, tous deux
 * immédiats. Pour le propriétaire, en revanche, le retard <em>se voit</em> — supprimer sa course
 * et la retrouver dans son fil est exactement le défaut qu'un utilisateur signale. Une éviction,
 * une seule, le supprime.
 *
 * <p>Après le projecteur dans l'ordre logique, mais rien ne le garantit : les deux écouteurs sont
 * indépendants. Si l'éviction passait avant la projection, la lecture suivante recharge la valeur
 * d'avant et la garde trente secondes — le cas est rare, borné, et le corriger demanderait de
 * coupler deux écouteurs qui n'ont pas à se connaître.
 */
@Component
class FeedHeadInvalidation {

    private final CacheGateway cache;
    private final FeedProjection projection;

    FeedHeadInvalidation(CacheGateway cache, FeedProjection projection) {
        this.cache = cache;
        this.projection = projection;
    }

    @ApplicationModuleListener
    void on(ActivityStarted event) {
        evictOwner(event.ownerId());
    }

    @ApplicationModuleListener
    void on(ActivityFinished event) {
        evictOwner(event.ownerId());
    }

    @ApplicationModuleListener
    void on(ActivityDiscarded event) {
        evictOwner(event.ownerId());
    }

    /**
     * Ces deux-là ne portent pas le propriétaire : il se relit dans la projection, tant qu'elle
     * contient encore la ligne. Si elle ne l'a plus, le projecteur est déjà passé — et l'entrée du
     * propriétaire aura expiré d'elle-même.
     */
    @ApplicationModuleListener
    void on(ActivityDeleted event) {
        evictOwnerOf(event.activityId());
    }

    @ApplicationModuleListener
    void on(ActivityVisibilityChanged event) {
        evictOwnerOf(event.activityId());
    }

    private void evictOwnerOf(ActivityId activityId) {
        projection.find(activityId).ifPresent(entry -> evictOwner(entry.ownerId()));
    }

    private void evictOwner(UserId ownerId) {
        cache.evict(CacheKey.feedHead(ownerId.toString()));
    }
}
