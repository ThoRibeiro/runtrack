package com.runtrack.engagement.internal.application.port;

import com.runtrack.engagement.internal.domain.interaction.Comment;
import com.runtrack.engagement.internal.domain.interaction.CommentId;
import com.runtrack.shared.id.ActivityId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Les commentaires en base. */
public interface CommentRepository {

    Comment save(Comment comment);

    Optional<Comment> findById(CommentId id);

    /**
     * Le fil d'une course, du plus ancien au plus récent, pagination par curseur.
     *
     * <p>Les commentaires supprimés sont rendus eux aussi : une réponse s'accroche à son parent, et
     * l'escamoter laisserait des réponses orphelines. C'est l'affichage qui remplace le texte.
     */
    List<Comment> ofActivity(ActivityId activityId, Optional<Instant> after, int limit);

    long countFor(ActivityId activityId);
}
