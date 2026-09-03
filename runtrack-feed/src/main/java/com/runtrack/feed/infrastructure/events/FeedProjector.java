package com.runtrack.feed.infrastructure.events;

import com.runtrack.course.CourseApi;
import com.runtrack.course.event.ActivityDeleted;
import com.runtrack.course.event.ActivityDiscarded;
import com.runtrack.course.event.ActivityFinished;
import com.runtrack.course.event.ActivityStarted;
import com.runtrack.course.event.ActivityVisibilityChanged;
import com.runtrack.engagement.event.ActivityCommented;
import com.runtrack.engagement.event.ActivityLiked;
import com.runtrack.engagement.event.ActivityUnliked;
import com.runtrack.engagement.event.CommentDeleted;
import com.runtrack.engagement.event.CommentReplied;
import com.runtrack.feed.usecases.port.FeedProjection;
import com.runtrack.platform.observability.CorrelationId;
import com.runtrack.feed.usecases.model.entry.FeedEntry;
import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.id.ActivityId;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Ce qui tient la projection du fil à jour.
 *
 * <p>Chaque écouteur touche exactement ce que son événement modifie : un « j'aime » n'écrit qu'un
 * compteur, une fin de course n'écrit que les statistiques. C'est ce qui les rend commutatifs —
 * deux événements indépendants arrivent dans n'importe quel ordre sans se détruire l'un l'autre.
 *
 * <p>{@code @ApplicationModuleListener} : après commit, en asynchrone, persisté avant traitement et
 * rejoué s'il n'aboutit pas. Le fil est donc en retard de quelques millisecondes sur la vérité —
 * ce que le §10 accepte pour une vue de lecture — mais jamais en désaccord durable avec elle.
 *
 * <p>Chaque écouteur rouvre la portée de corrélation à partir de l'identifiant que son événement
 * transporte (§12) : sans ce geste, une ligne de fil apparue de travers n'aurait aucun lien
 * traçable avec la requête qui l'a provoquée.
 *
 * <p>Les ajustements de compteur sont volontairement <b>non idempotents</b> : {@code +1} rejoué
 * compte deux fois. Le registre ne rejoue que ce qui n'a pas abouti, donc ce qui n'a pas été
 * appliqué ; rendre l'opération idempotente demanderait de mémoriser chaque « j'aime » projeté,
 * c'est-à-dire de recopier la table de {@code engagement} pour un compteur d'affichage.
 */
@Component
class FeedProjector {

    private static final Logger LOG = LoggerFactory.getLogger(FeedProjector.class);

    private final FeedProjection projection;
    private final CourseApi courses;

    FeedProjector(FeedProjection projection, CourseApi courses) {
        this.projection = projection;
        this.courses = courses;
    }

    @ApplicationModuleListener
    void onActivityStarted(ActivityStarted event) {
        CorrelationId.resume(event.correlationId(), () -> projectFrom(event.activityId()));
    }

    @ApplicationModuleListener
    void onActivityFinished(ActivityFinished event) {
        CorrelationId.resume(event.correlationId(), () -> projectFrom(event.activityId()));
    }

    /** Une course abandonnée sort du fil : elle est conservée, mais hors de tout affichage (§3). */
    @ApplicationModuleListener
    void onActivityDiscarded(ActivityDiscarded event) {
        CorrelationId.resume(event.correlationId(), () -> projection.remove(event.activityId()));
    }

    @ApplicationModuleListener
    void onActivityDeleted(ActivityDeleted event) {
        CorrelationId.resume(event.correlationId(), () -> projection.remove(event.activityId()));
    }

    @ApplicationModuleListener
    void onVisibilityChanged(ActivityVisibilityChanged event) {
        CorrelationId.resume(event.correlationId(), () -> projection.updateVisibility(event.activityId(), event.effectiveScope()));
    }

    @ApplicationModuleListener
    void onActivityLiked(ActivityLiked event) {
        CorrelationId.resume(event.correlationId(), () -> projection.adjustLikes(event.activityId(), 1));
    }

    @ApplicationModuleListener
    void onActivityUnliked(ActivityUnliked event) {
        CorrelationId.resume(event.correlationId(), () -> projection.adjustLikes(event.activityId(), -1));
    }

    @ApplicationModuleListener
    void onActivityCommented(ActivityCommented event) {
        CorrelationId.resume(event.correlationId(), () -> projection.adjustComments(event.activityId(), 1));
    }

    @ApplicationModuleListener
    void onCommentReplied(CommentReplied event) {
        CorrelationId.resume(event.correlationId(), () -> projection.adjustComments(event.activityId(), 1));
    }

    @ApplicationModuleListener
    void onCommentDeleted(CommentDeleted event) {
        CorrelationId.resume(event.correlationId(), () -> projection.adjustComments(event.activityId(), -1));
    }

    /**
     * Relit le résumé auprès de {@code course} plutôt que de recopier l'événement.
     *
     * <p>Les événements portent ce dont les notifications ont besoin, pas ce qu'une ligne de fil
     * affiche — titre, type, statut. Les y ajouter ferait grossir un contrat inter-modules à chaque
     * colonne nouvelle du fil ; un appel à {@code CourseApi} passe par le cache du §6 et coûte
     * bien moins qu'un contrat qu'on ne peut plus resserrer.
     */
    private void projectFrom(ActivityId activityId) {
        Optional<com.runtrack.course.ActivitySummary> summary = courses.summary(activityId);
        if (summary.isEmpty()) {
            // La course a disparu entre l'événement et son traitement : rien à projeter, et la
            // suppression est déjà passée ou passera.
            LOG.debug("Course {} introuvable, projection ignorée", activityId);
            return;
        }
        var found = summary.get();
        projection.upsert(new FeedEntry(
                found.id(),
                found.ownerId(),
                found.type(),
                found.title(),
                found.status(),
                AudienceScope.valueOf(found.effectiveScope()),
                found.distanceMeters(),
                found.movingTimeSeconds(),
                found.startedAt(),
                found.endedAt(),
                0,
                0,
                found.previewPolyline()));
    }
}
