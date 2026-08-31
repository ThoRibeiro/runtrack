package com.runtrack.engagement.infrastructure.repository;

import com.runtrack.engagement.usecases.model.interaction.ActivityCounters;
import com.runtrack.engagement.usecases.port.ActivityCountersRepository;
import com.runtrack.shared.id.ActivityId;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Les deux compteurs en une requête : ils sont toujours lus ensemble. */
@Repository
class JdbcActivityCountersRepository implements ActivityCountersRepository {

    private final NamedParameterJdbcTemplate jdbc;

    JdbcActivityCountersRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ActivityCounters countersOf(ActivityId activityId) {
        Map<String, Object> parameters = Map.of("activity", activityId.value());
        return jdbc.query("""
                SELECT
                    (SELECT count(*) FROM likes WHERE activity_id = :activity) AS likes,
                    -- Les commentaires supprimés ne comptent pas : le chiffre affiché doit
                    -- correspondre à ce qui se lit en dessous.
                    (SELECT count(*) FROM comments
                     WHERE activity_id = :activity AND deleted_at IS NULL) AS comments
                """, parameters,
                rows -> rows.next()
                        ? new ActivityCounters(rows.getLong("likes"), rows.getLong("comments"))
                        : ActivityCounters.NONE);
    }
}
