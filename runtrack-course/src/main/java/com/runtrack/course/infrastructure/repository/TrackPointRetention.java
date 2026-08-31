package com.runtrack.course.infrastructure.repository;

import com.runtrack.course.usecases.port.ActivityArchive;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * La rétention des points bruts, décidée au lot 1 : 90 jours après l'historisation.
 *
 * <p>Au-delà, la polyline, la géométrie, les splits et les statistiques suffisent à tout afficher.
 * Les points bruts ne servent plus qu'à recalculer ou à exporter un GPX — et c'est la conséquence
 * assumée de la décision : <b>l'export n'est possible que dans les 90 jours</b>. Une sortie de
 * trois heures pèse dix mille lignes ; les garder pour toujours ferait de {@code track_points} la
 * plus grosse table du système, au service d'un cas d'usage rare.
 *
 * <p>Ne purge que ce qui est <b>archivé</b>. Une course terminée dont l'historisation aurait échoué
 * garde donc ses points, et c'est l'ordre qu'on veut : on n'efface pas la source avant d'être sûr
 * d'avoir gardé ce qu'on en tire.
 */
@Component
class TrackPointRetention {

    static final Duration RETENTION = Duration.ofDays(90);

    private static final Logger LOG = LoggerFactory.getLogger(TrackPointRetention.class);

    /** Par lots : une purge non bornée verrouillerait des millions de lignes d'un seul coup. */
    private static final int BATCH_SIZE = 200;

    private final ActivityArchive archive;
    private final Clock clock;
    private final Counter purged;

    TrackPointRetention(ActivityArchive archive, Clock clock, MeterRegistry meters) {
        this.archive = archive;
        this.clock = clock;
        this.purged = Counter.builder("runtrack.track_points.purged")
                .description("Courses dont les points bruts ont été effacés après rétention")
                .register(meters);
    }

    /** À 3 h 40, quand personne ne court : la purge tient des verrous sur une grosse table. */
    @Scheduled(cron = "0 40 3 * * *")
    public void purgeExpiredPoints() {
        Instant now = clock.instant();
        int erased = archive.purgePointsArchivedBefore(now.minus(RETENTION), now, BATCH_SIZE);
        if (erased > 0) {
            purged.increment(erased);
            LOG.info("Points bruts effacés pour {} course(s) archivée(s) il y a plus de {} jours",
                    erased, RETENTION.toDays());
        }
    }
}
