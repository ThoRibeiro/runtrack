package com.runtrack.course.usecases.port;

import com.runtrack.course.usecases.model.activity.Activity;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Le port de persistance des courses. Aucun type JPA dans ces signatures. */
public interface ActivityRepository {

    Optional<Activity> findById(ActivityId id);

    List<Activity> findAllById(Collection<ActivityId> ids);

    /** Les courses d'un utilisateur, les plus récentes d'abord, pagination par curseur. */
    List<Activity> findByOwner(UserId ownerId, Optional<java.time.Instant> before, int limit);

    /** Les courses en cours d'un ensemble d'utilisateurs, pour l'écran « en direct ». */
    List<Activity> findLiveOf(Collection<UserId> ownerIds);

    Activity save(Activity activity);

    void delete(ActivityId id);
}
