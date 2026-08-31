package com.runtrack.course.infrastructure.repository;

import com.runtrack.course.usecases.port.TrackPointRepository;
import com.runtrack.course.usecases.model.track.TrackPoint;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.measure.Elevation;
import com.runtrack.shared.measure.GeoPoint;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Les points de trace, en JDBC direct plutôt qu'en JPA.
 *
 * <p>Un point de trace n'est pas un agrégat : il n'a pas de cycle de vie, personne ne le
 * modifie, et il arrive par dizaines de milliers. Passer par un {@code EntityManager}
 * ferait grossir le contexte de persistance à chaque lot pour aucun bénéfice ; le batch
 * JDBC insère tout en un aller-retour.
 *
 * <p>{@code ON CONFLICT DO NOTHING} est le dernier filet de l'idempotence : même si le
 * curseur de l'accumulateur était contourné, la base refuserait le doublon sans faire
 * échouer le lot entier.
 */
@Repository
class JdbcTrackPointRepository implements TrackPointRepository {

    private static final String INSERT = """
            INSERT INTO track_points (
                activity_id, sequence_number, latitude, longitude, elevation,
                recorded_at, accuracy_meters, heart_rate, cadence, geom)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?,
                ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography)
            ON CONFLICT (activity_id, sequence_number) DO NOTHING
            """;

    private static final String SELECT_ALL = """
            SELECT sequence_number, latitude, longitude, elevation, recorded_at,
                   accuracy_meters, heart_rate, cadence
            FROM track_points WHERE activity_id = ? ORDER BY sequence_number
            """;

    private static final String SELECT_LAST = """
            SELECT sequence_number, latitude, longitude, elevation, recorded_at,
                   accuracy_meters, heart_rate, cadence
            FROM track_points WHERE activity_id = ? ORDER BY sequence_number DESC LIMIT 1
            """;

    private static final String SELECT_RECENT = """
            SELECT * FROM (
                SELECT sequence_number, latitude, longitude, elevation, recorded_at,
                       accuracy_meters, heart_rate, cadence
                FROM track_points WHERE activity_id = ? ORDER BY sequence_number DESC LIMIT ?
            ) recent ORDER BY sequence_number
            """;

    private static final RowMapper<TrackPoint> MAPPER = (rs, rowNumber) -> new TrackPoint(
            rs.getInt("sequence_number"),
            new GeoPoint(rs.getDouble("latitude"), rs.getDouble("longitude")),
            Elevation.ofMeters(rs.getDouble("elevation")),
            rs.getObject("recorded_at", java.time.OffsetDateTime.class).toInstant(),
            rs.getDouble("accuracy_meters"),
            optionalInt(rs.getObject("heart_rate", Integer.class)),
            optionalInt(rs.getObject("cadence", Integer.class)));

    private final JdbcTemplate jdbc;

    JdbcTrackPointRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int appendAll(ActivityId activityId, List<TrackPoint> pointsToAppend) {
        if (pointsToAppend.isEmpty()) {
            return 0;
        }
        int[] written = jdbc.batchUpdate(INSERT, new BatchPreparedStatementSetter() {

            @Override
            public void setValues(PreparedStatement statement, int index) throws SQLException {
                TrackPoint point = pointsToAppend.get(index);
                statement.setObject(1, activityId.value());
                statement.setInt(2, point.sequenceNumber());
                statement.setDouble(3, point.position().latitude());
                statement.setDouble(4, point.position().longitude());
                statement.setDouble(5, point.elevation().meters());
                statement.setObject(6, point.recordedAt().atOffset(java.time.ZoneOffset.UTC));
                statement.setDouble(7, point.accuracyMeters());
                setNullableInt(statement, 8, point.heartRate());
                setNullableInt(statement, 9, point.cadence());
                // ST_MakePoint attend longitude puis latitude, dans cet ordre.
                statement.setDouble(10, point.position().longitude());
                statement.setDouble(11, point.position().latitude());
            }

            @Override
            public int getBatchSize() {
                return pointsToAppend.size();
            }
        });
        return java.util.Arrays.stream(written).map(count -> Math.max(count, 0)).sum();
    }

    @Override
    public Optional<TrackPoint> findLast(ActivityId activityId) {
        return jdbc.query(SELECT_LAST, MAPPER, activityId.value()).stream().findFirst();
    }

    @Override
    public List<TrackPoint> findRecent(ActivityId activityId, int limit) {
        // Les derniers en base, remis à l'endroit : le client trace du plus ancien au plus récent.
        return jdbc.query(SELECT_RECENT, MAPPER, activityId.value(), limit);
    }

    @Override
    public List<TrackPoint> findAll(ActivityId activityId) {
        return jdbc.query(SELECT_ALL, MAPPER, activityId.value());
    }

    @Override
    public void deleteAll(ActivityId activityId) {
        jdbc.update("DELETE FROM track_points WHERE activity_id = ?", activityId.value());
    }

    @Override
    public int count(ActivityId activityId) {
        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM track_points WHERE activity_id = ?", Integer.class, activityId.value());
        return total == null ? 0 : total;
    }

    private static void setNullableInt(PreparedStatement statement, int index, OptionalInt value)
            throws SQLException {
        if (value.isPresent()) {
            statement.setInt(index, value.getAsInt());
        } else {
            statement.setNull(index, Types.INTEGER);
        }
    }

    private static OptionalInt optionalInt(Integer value) {
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }
}
