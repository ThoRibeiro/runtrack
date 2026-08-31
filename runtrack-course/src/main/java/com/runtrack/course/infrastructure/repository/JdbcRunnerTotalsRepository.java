package com.runtrack.course.infrastructure.repository;

import com.runtrack.course.usecases.model.stats.RunnerTotals;
import com.runtrack.course.usecases.port.RunnerTotalsRepository;
import com.runtrack.shared.id.UserId;
import com.runtrack.shared.measure.Distance;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Le bilan, en deux requêtes d'agrégation.
 *
 * <p>Deux et pas une par type : la somme globale et le détail par type se calculent d'un seul
 * parcours chacun, là où boucler sur les types en ferait une par discipline. Et deux plutôt qu'une
 * seule avec {@code GROUPING SETS}, parce que le gain — un aller-retour — ne vaut pas une requête
 * que personne ne relira sans effort.
 *
 * <p><b>Jointure interne au module.</b> {@code activities} et {@code activity_stats} appartiennent
 * toutes deux à {@code course} : le §10 n'interdit que les jointures <em>inter-modules</em>.
 */
@Repository
class JdbcRunnerTotalsRepository implements RunnerTotalsRepository {

    /**
     * Seules les courses terminées comptent : une course en cours n'a pas de total, une course
     * abandonnée n'en a plus, et une course en pause finira bien par tomber dans l'un des deux.
     */
    private static final String WHERE = """
            FROM activities a JOIN activity_stats s ON s.activity_id = a.id
            WHERE a.owner_id = :owner AND a.status = 'Finished'
            """;

    private static final String SINCE = " AND a.started_at >= :since";

    private final NamedParameterJdbcTemplate jdbc;

    JdbcRunnerTotalsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public RunnerTotals totalsOf(UserId ownerId, Optional<Instant> since) {
        var parameters = new java.util.HashMap<String, Object>();
        parameters.put("owner", ownerId.value());
        since.ifPresent(from -> parameters.put("since", from.atOffset(ZoneOffset.UTC)));
        String scope = WHERE + (since.isPresent() ? SINCE : "");

        RunnerTotals overall = jdbc.query("""
                SELECT count(*) AS activities,
                       coalesce(sum(s.distance_meters), 0) AS distance,
                       coalesce(sum(s.moving_time_seconds), 0) AS moving_time,
                       coalesce(sum(s.elevation_gain), 0) AS elevation
                """ + scope,
                parameters,
                rows -> rows.next()
                        ? new RunnerTotals(
                                rows.getLong("activities"),
                                Distance.ofMeters(rows.getDouble("distance")),
                                Duration.ofSeconds(rows.getLong("moving_time")),
                                rows.getDouble("elevation"),
                                List.of())
                        : RunnerTotals.empty());

        if (overall == null || overall.activityCount() == 0) {
            // Aucune course : inutile d'aller chercher un détail par type qui sera vide.
            return RunnerTotals.empty();
        }

        List<RunnerTotals.ByType> byType = jdbc.query("""
                SELECT a.type,
                       count(*) AS activities,
                       coalesce(sum(s.distance_meters), 0) AS distance,
                       coalesce(sum(s.moving_time_seconds), 0) AS moving_time
                """ + scope + " GROUP BY a.type ORDER BY sum(s.distance_meters) DESC",
                parameters,
                (rows, rowNumber) -> new RunnerTotals.ByType(
                        rows.getString("type"),
                        rows.getLong("activities"),
                        Distance.ofMeters(rows.getDouble("distance")),
                        Duration.ofSeconds(rows.getLong("moving_time"))));

        return new RunnerTotals(overall.activityCount(), overall.distance(), overall.movingTime(),
                overall.elevationGain(), new ArrayList<>(byType));
    }
}
