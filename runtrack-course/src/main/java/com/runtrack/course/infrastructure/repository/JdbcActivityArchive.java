package com.runtrack.course.infrastructure.repository;

import com.runtrack.course.usecases.port.ActivityArchive;
import com.runtrack.course.usecases.model.stats.Split;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.measure.Distance;
import com.runtrack.shared.measure.GeoPoint;
import com.runtrack.shared.measure.Pace;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * La trace historisée et ses splits, en JDBC.
 *
 * <p>La géométrie est construite en base par {@code ST_GeomFromText} plutôt qu'assemblée côté Java :
 * PostGIS valide la ligne, et une chaîne WKT construite ici serait la seule pièce du système à
 * n'être vérifiée par personne.
 */
@Repository
class JdbcActivityArchive implements ActivityArchive {

    private static final String UPSERT_TRACK = """
            INSERT INTO activity_tracks (activity_id, polyline, point_count, raw_point_count,
                                         geom, frozen_at, points_purged_at)
            VALUES (?, ?, ?, ?, %s, ?, NULL)
            ON CONFLICT (activity_id) DO UPDATE SET
                polyline = EXCLUDED.polyline,
                point_count = EXCLUDED.point_count,
                raw_point_count = EXCLUDED.raw_point_count,
                geom = EXCLUDED.geom,
                frozen_at = EXCLUDED.frozen_at
            """;

    private static final String INSERT_SPLIT = """
            INSERT INTO activity_splits (activity_id, kilometer_index, distance_meters, time_seconds,
                                         pace_seconds_per_km, elevation_gain, average_heart_rate)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (activity_id, kilometer_index) DO UPDATE SET
                distance_meters = EXCLUDED.distance_meters,
                time_seconds = EXCLUDED.time_seconds,
                pace_seconds_per_km = EXCLUDED.pace_seconds_per_km,
                elevation_gain = EXCLUDED.elevation_gain,
                average_heart_rate = EXCLUDED.average_heart_rate
            """;

    private static final RowMapper<Split> SPLIT_MAPPER = (rs, rowNumber) -> new Split(
            rs.getInt("kilometer_index"),
            Distance.ofMeters(rs.getDouble("distance_meters")),
            Duration.ofSeconds(rs.getLong("time_seconds")),
            Optional.ofNullable(rs.getObject("pace_seconds_per_km", Long.class))
                    .map(seconds -> new Pace(Duration.ofSeconds(seconds))),
            rs.getDouble("elevation_gain"),
            Optional.ofNullable(rs.getObject("average_heart_rate", Double.class))
                    .map(OptionalDouble::of).orElse(OptionalDouble.empty()));

    private final JdbcTemplate jdbc;

    JdbcActivityArchive(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(ArchivedTrack track, List<Split> splits) {
        // Une ligne demande au moins deux points : au premier, il n'y a qu'une position, et PostGIS
        // refuserait un LINESTRING dégénéré.
        boolean hasLine = track.positions().size() >= 2;
        String geometry = hasLine ? "ST_GeomFromText(?, 4326)::geography" : "NULL";

        if (hasLine) {
            jdbc.update(UPSERT_TRACK.formatted(geometry),
                    track.activityId().value(), track.polyline(), track.pointCount(),
                    track.rawPointCount(), wktOf(track.positions()),
                    track.frozenAt().atOffset(ZoneOffset.UTC));
        } else {
            jdbc.update(UPSERT_TRACK.formatted(geometry),
                    track.activityId().value(), track.polyline(), track.pointCount(),
                    track.rawPointCount(), track.frozenAt().atOffset(ZoneOffset.UTC));
        }
        saveSplits(track.activityId(), splits);
    }

    private void saveSplits(ActivityId activityId, List<Split> splits) {
        if (splits.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(INSERT_SPLIT, new BatchPreparedStatementSetter() {

            @Override
            public void setValues(PreparedStatement statement, int index) throws SQLException {
                Split split = splits.get(index);
                statement.setObject(1, activityId.value());
                statement.setInt(2, split.kilometerIndex());
                statement.setDouble(3, split.distance().meters());
                statement.setLong(4, split.time().toSeconds());
                setNullableLong(statement, 5, split.pace()
                        .map(pace -> pace.perKilometer().toSeconds()));
                statement.setDouble(6, split.elevationGain());
                if (split.averageHeartRate().isPresent()) {
                    statement.setDouble(7, split.averageHeartRate().getAsDouble());
                } else {
                    statement.setNull(7, Types.DOUBLE);
                }
            }

            @Override
            public int getBatchSize() {
                return splits.size();
            }
        });
    }

    @Override
    public Optional<ArchivedTrack> find(ActivityId activityId) {
        return jdbc.query("""
                SELECT activity_id, polyline, point_count, raw_point_count, frozen_at, points_purged_at
                FROM activity_tracks WHERE activity_id = ?
                """, (rs, rowNumber) -> new ArchivedTrack(
                        new ActivityId(rs.getObject("activity_id", UUID.class)),
                        rs.getString("polyline"),
                        rs.getInt("point_count"),
                        rs.getInt("raw_point_count"),
                        // Les positions ne sont pas relues : ce que le client affiche est la
                        // polyline, et la géométrie ne sert qu'aux requêtes spatiales en base.
                        List.of(),
                        rs.getObject("frozen_at", OffsetDateTime.class).toInstant(),
                        Optional.ofNullable(rs.getObject("points_purged_at", OffsetDateTime.class))
                                .map(OffsetDateTime::toInstant)),
                activityId.value()).stream().findFirst();
    }

    @Override
    public List<Split> splitsOf(ActivityId activityId) {
        return jdbc.query("""
                SELECT kilometer_index, distance_meters, time_seconds, pace_seconds_per_km,
                       elevation_gain, average_heart_rate
                FROM activity_splits WHERE activity_id = ? ORDER BY kilometer_index
                """, SPLIT_MAPPER, activityId.value());
    }

    @Override
    public void delete(ActivityId activityId) {
        jdbc.update("DELETE FROM activity_splits WHERE activity_id = ?", activityId.value());
        jdbc.update("DELETE FROM activity_tracks WHERE activity_id = ?", activityId.value());
    }

    @Override
    public int purgePointsArchivedBefore(Instant cutoff, Instant purgedAt, int batchSize) {
        // Par lots : une purge non bornée verrouillerait des millions de lignes de track_points
        // d'un coup, et rien d'autre ne passerait pendant ce temps.
        List<UUID> due = jdbc.queryForList("""
                SELECT activity_id FROM activity_tracks
                WHERE points_purged_at IS NULL AND frozen_at < ?
                ORDER BY frozen_at LIMIT ?
                """, UUID.class, cutoff.atOffset(ZoneOffset.UTC), batchSize);

        due.forEach(activityId -> {
            jdbc.update("DELETE FROM track_points WHERE activity_id = ?", activityId);
            jdbc.update("UPDATE activity_tracks SET points_purged_at = ? WHERE activity_id = ?",
                    purgedAt.atOffset(ZoneOffset.UTC), activityId);
        });
        return due.size();
    }

    /** La ligne au format WKT : {@code LINESTRING(lon lat, lon lat, …)} — longitude d'abord. */
    private static String wktOf(List<GeoPoint> positions) {
        return positions.stream()
                .map(position -> position.longitude() + " " + position.latitude())
                .collect(Collectors.joining(", ", "LINESTRING(", ")"));
    }

    private static void setNullableLong(PreparedStatement statement, int index, Optional<Long> value)
            throws SQLException {

        if (value.isPresent()) {
            statement.setLong(index, value.get());
        } else {
            statement.setNull(index, Types.BIGINT);
        }
    }
}
