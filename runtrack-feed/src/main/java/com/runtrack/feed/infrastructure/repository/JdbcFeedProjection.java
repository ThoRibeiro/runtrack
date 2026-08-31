package com.runtrack.feed.infrastructure.repository;

import com.runtrack.feed.usecases.port.FeedProjection;
import com.runtrack.feed.usecases.model.entry.FeedEntry;
import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * La projection en JDBC : des écritures ciblées, et une seule lecture.
 *
 * <p>Chaque événement touche exactement les colonnes qui le concernent. Recharger la ligne pour la
 * réécrire entière ferait qu'un « j'aime » arrivé entre-temps serait écrasé par un
 * {@code ActivityFinished} — deux événements indépendants qui se marcheraient dessus sans qu'aucune
 * erreur ne le signale.
 */
@Repository
class JdbcFeedProjection implements FeedProjection {

    private static final String COLUMNS = """
            activity_id, owner_id, type, title, status, effective_scope,
            distance_meters, moving_time_seconds, started_at, ended_at, like_count, comment_count
            """;

    private static final RowMapper<FeedEntry> MAPPER = (rs, rowNumber) -> new FeedEntry(
            new ActivityId(rs.getObject("activity_id", UUID.class)),
            new UserId(rs.getObject("owner_id", UUID.class)),
            rs.getString("type"),
            rs.getString("title"),
            rs.getString("status"),
            AudienceScope.valueOf(rs.getString("effective_scope")),
            rs.getDouble("distance_meters"),
            rs.getLong("moving_time_seconds"),
            rs.getObject("started_at", OffsetDateTime.class).toInstant(),
            Optional.ofNullable(rs.getObject("ended_at", OffsetDateTime.class))
                    .map(OffsetDateTime::toInstant),
            rs.getLong("like_count"),
            rs.getLong("comment_count"));

    private final NamedParameterJdbcTemplate jdbc;

    JdbcFeedProjection(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void upsert(FeedEntry entry) {
        var parameters = new HashMap<String, Object>();
        parameters.put("activityId", entry.activityId().value());
        parameters.put("ownerId", entry.ownerId().value());
        parameters.put("type", entry.type());
        parameters.put("title", entry.title());
        parameters.put("status", entry.status());
        parameters.put("scope", entry.effectiveScope().name());
        parameters.put("distance", entry.distanceMeters());
        parameters.put("movingTime", entry.movingTimeSeconds());
        parameters.put("startedAt", entry.startedAt().atOffset(ZoneOffset.UTC));
        parameters.put("endedAt", entry.endedAt().map(at -> at.atOffset(ZoneOffset.UTC)).orElse(null));

        // Les compteurs ne figurent pas dans la mise à jour : ils appartiennent à `engagement` et
        // seraient remis à zéro par une fin de course arrivée après un premier « j'aime ».
        jdbc.update("""
                INSERT INTO feed_entries (activity_id, owner_id, type, title, status, effective_scope,
                                          distance_meters, moving_time_seconds, started_at, ended_at)
                VALUES (:activityId, :ownerId, :type, :title, :status, :scope,
                        :distance, :movingTime, :startedAt, :endedAt)
                ON CONFLICT (activity_id) DO UPDATE SET
                    type = EXCLUDED.type,
                    title = EXCLUDED.title,
                    status = EXCLUDED.status,
                    effective_scope = EXCLUDED.effective_scope,
                    distance_meters = EXCLUDED.distance_meters,
                    moving_time_seconds = EXCLUDED.moving_time_seconds,
                    ended_at = EXCLUDED.ended_at
                """, parameters);
    }

    @Override
    public void updateVisibility(ActivityId activityId, String effectiveScope) {
        jdbc.update("UPDATE feed_entries SET effective_scope = :scope WHERE activity_id = :id",
                Map.of("scope", effectiveScope, "id", activityId.value()));
    }

    @Override
    public void remove(ActivityId activityId) {
        jdbc.update("DELETE FROM feed_entries WHERE activity_id = :id",
                Map.of("id", activityId.value()));
    }

    @Override
    public void adjustLikes(ActivityId activityId, int delta) {
        adjust("like_count", activityId, delta);
    }

    @Override
    public void adjustComments(ActivityId activityId, int delta) {
        adjust("comment_count", activityId, delta);
    }

    /**
     * Le compteur ne descend jamais sous zéro.
     *
     * <p>Un événement rejoué de suppression, ou un « j'aime » retiré dont la pose n'avait pas été
     * projetée, laisserait sinon un compteur négatif à l'écran — visible, et inexplicable.
     */
    private void adjust(String column, ActivityId activityId, int delta) {
        jdbc.update("""
                UPDATE feed_entries SET %s = GREATEST(%s + :delta, 0) WHERE activity_id = :id
                """.formatted(column, column),
                Map.of("delta", delta, "id", activityId.value()));
    }

    @Override
    public Optional<FeedEntry> find(ActivityId activityId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM feed_entries WHERE activity_id = :id",
                Map.of("id", activityId.value()), MAPPER).stream().findFirst();
    }

    @Override
    public List<FeedEntry> page(Collection<UserId> owners, Optional<Instant> before, int limit) {
        if (owners.isEmpty()) {
            return List.of();
        }
        var parameters = new HashMap<String, Object>();
        parameters.put("owners", owners.stream().map(UserId::value).toList());
        parameters.put("limit", limit);

        var sql = new StringBuilder("SELECT " + COLUMNS + """
                 FROM feed_entries
                 WHERE owner_id IN (:owners) AND effective_scope <> 'PRIVATE'
                """);
        before.ifPresent(cursor -> {
            sql.append(" AND started_at < :before");
            parameters.put("before", cursor.atOffset(ZoneOffset.UTC));
        });
        sql.append(" ORDER BY started_at DESC LIMIT :limit");

        return jdbc.query(sql.toString(), parameters, MAPPER);
    }
}
