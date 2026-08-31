package com.runtrack.engagement.usecases.port;

import com.runtrack.engagement.usecases.model.interaction.Like;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import java.util.List;

/** Les « j'aime » en base. */
public interface LikeRepository {

    /** @return {@code true} si le « j'aime » vient d'être posé, {@code false} s'il existait déjà */
    boolean add(Like like);

    /** @return {@code true} si un « j'aime » a été retiré */
    boolean remove(ActivityId activityId, UserId userId);

    boolean exists(ActivityId activityId, UserId userId);

    long countFor(ActivityId activityId);

    List<Like> ofActivity(ActivityId activityId, int limit);
}
