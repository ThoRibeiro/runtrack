package com.runtrack.engagement.internal.infra.jpa;

import com.runtrack.engagement.internal.application.port.LikeRepository;
import com.runtrack.engagement.internal.domain.interaction.Like;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Les « j'aime », en JDBC : deux colonnes et une date, jamais modifiés, comptés par agrégation.
 *
 * <p>{@code ON CONFLICT DO NOTHING} rend l'écriture idempotente et, surtout, dit à l'appelant si
 * quelque chose s'est réellement passé — c'est ce qui évite de publier un second
 * {@code ActivityLiked} quand un client renvoie un clic dont il a perdu la réponse.
 */
@Repository
class JdbcLikeRepository implements LikeRepository {

    private static final RowMapper<Like> MAPPER = (rs, rowNumber) -> new Like(
            new ActivityId(rs.getObject("activity_id", UUID.class)),
            new UserId(rs.getObject("user_id", UUID.class)),
            rs.getObject("liked_at", OffsetDateTime.class).toInstant());

    private final JdbcTemplate jdbc;

    JdbcLikeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean add(Like like) {
        return jdbc.update("""
                INSERT INTO likes (activity_id, user_id, liked_at) VALUES (?, ?, ?)
                ON CONFLICT (activity_id, user_id) DO NOTHING
                """, like.activityId().value(), like.userId().value(),
                like.at().atOffset(ZoneOffset.UTC)) > 0;
    }

    @Override
    public boolean remove(ActivityId activityId, UserId userId) {
        return jdbc.update("DELETE FROM likes WHERE activity_id = ? AND user_id = ?",
                activityId.value(), userId.value()) > 0;
    }

    @Override
    public boolean exists(ActivityId activityId, UserId userId) {
        Integer found = jdbc.queryForObject(
                "SELECT count(*) FROM likes WHERE activity_id = ? AND user_id = ?",
                Integer.class, activityId.value(), userId.value());
        return found != null && found > 0;
    }

    @Override
    public long countFor(ActivityId activityId) {
        Long total = jdbc.queryForObject("SELECT count(*) FROM likes WHERE activity_id = ?",
                Long.class, activityId.value());
        return total == null ? 0 : total;
    }

    @Override
    public List<Like> ofActivity(ActivityId activityId, int limit) {
        return jdbc.query("""
                SELECT activity_id, user_id, liked_at FROM likes
                WHERE activity_id = ? ORDER BY liked_at DESC LIMIT ?
                """, MAPPER, activityId.value(), limit);
    }
}
