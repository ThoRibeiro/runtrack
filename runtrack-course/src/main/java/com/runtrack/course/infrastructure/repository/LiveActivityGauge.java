package com.runtrack.course.infrastructure.repository;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Le nombre de courses en cours (§12).
 *
 * <p>Rafraîchi par un travail planifié plutôt que compté à chaque relevé : une jauge Micrometer
 * est interrogée à chaque scrutation Prometheus, et brancher un {@code count(*)} dessus ferait
 * qu'augmenter la fréquence de supervision augmenterait la charge de la base — exactement ce
 * qu'une métrique ne doit pas faire.
 */
@Component
class LiveActivityGauge {

    private static final Logger LOG = LoggerFactory.getLogger(LiveActivityGauge.class);

    private final JdbcTemplate jdbc;
    private final AtomicLong live = new AtomicLong();

    LiveActivityGauge(JdbcTemplate jdbc, MeterRegistry meters) {
        this.jdbc = jdbc;
        Gauge.builder("runtrack.activities.live", live, AtomicLong::doubleValue)
                .description("Courses en cours d'enregistrement, pause comprise")
                .register(meters);
    }

    @Scheduled(fixedDelayString = "${runtrack.metrics.live-activities-interval:30s}")
    void refresh() {
        try {
            Long counted = jdbc.queryForObject("""
                    SELECT count(*) FROM activities WHERE status IN ('Live', 'Paused')
                    """, Long.class);
            live.set(counted == null ? 0 : counted);
        } catch (RuntimeException unavailable) {
            // Une métrique indisponible ne doit pas remonter : elle garde sa dernière valeur, et
            // c'est la métrique de santé de la base qui dira ce qui se passe réellement.
            LOG.debug("Jauge des courses en cours non rafraîchie : {}", unavailable.getMessage());
        }
    }
}
