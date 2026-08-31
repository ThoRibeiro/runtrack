package com.runtrack.engagement.usecases.port;

import com.runtrack.engagement.usecases.model.interaction.ActivityCounters;
import com.runtrack.shared.id.ActivityId;

/**
 * Les compteurs d'une course, comptés en base.
 *
 * <p>Un port à part de {@link LikeRepository} et {@link CommentRepository}, qui rendent des faits :
 * celui-ci ne rend que des nombres, et c'est ce qui le rend cachable sans risque. Cacher une liste
 * de « j'aime » périmée afficherait des noms faux ; cacher un compteur d'une minute affiche un
 * chiffre légèrement en retard, ce qui est le comportement attendu d'un compteur social.
 */
public interface ActivityCountersRepository {

    ActivityCounters countersOf(ActivityId activityId);
}
