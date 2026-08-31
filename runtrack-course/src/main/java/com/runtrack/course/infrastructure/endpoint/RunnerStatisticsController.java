package com.runtrack.course.infrastructure.endpoint;

import static com.runtrack.course.infrastructure.endpoint.Principals.requireUser;

import com.runtrack.course.infrastructure.dto.ActivityDtos;
import com.runtrack.course.usecases.model.stats.RunnerTotals;
import com.runtrack.course.usecases.model.stats.StatsPeriod;
import com.runtrack.course.usecases.service.RunnerStatistics;
import com.runtrack.shared.access.Viewer;
import com.runtrack.shared.id.UserId;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Le bilan personnel : {@code GET /users/me/stats?period=}.
 *
 * <p>Servi par {@code course} bien que l'URL commence par {@code /users/me} — les courses lui
 * appartiennent, et faire dépendre {@code user} de {@code course} fermerait un cycle. C'est déjà
 * l'arrangement de {@code /users/me/devices}, porté par {@code notification} : une URL décrit ce
 * que le client demande, pas quel module le sert.
 */
@RestController
@RequestMapping("/api/v1")
class RunnerStatisticsController {

    private final RunnerStatistics statistics;
    private final Clock clock;

    RunnerStatisticsController(RunnerStatistics statistics, Clock clock) {
        this.statistics = statistics;
        this.clock = clock;
    }

    /**
     * @param period {@code WEEK}, {@code MONTH}, {@code YEAR} ou {@code ALL} ; par défaut le mois
     * @param zone le fuseau du client — la limite d'une semaine n'a de sens que quelque part, et
     *     découper à minuit UTC ferait basculer une sortie du dimanche soir dans la semaine
     *     suivante pour la moitié de la planète
     */
    @GetMapping("/users/me/stats")
    ActivityDtos.RunnerTotalsResponse myStats(
            @org.springframework.security.core.annotation.AuthenticationPrincipal Viewer viewer,
            @RequestParam(required = false, defaultValue = "MONTH") String period,
            @RequestParam(required = false) String zone) {

        UserId runner = requireUser(viewer);
        StatsPeriod window = StatsPeriod.valueOf(period.toUpperCase(java.util.Locale.ROOT));
        ZoneId timeZone = zoneOf(zone);

        return toResponse(window, timeZone, statistics.of(runner, window, timeZone));
    }

    /** Un fuseau inconnu est une donnée invalide : 422, et non un bilan calculé ailleurs en silence. */
    private static ZoneId zoneOf(String zone) {
        if (zone == null || zone.isBlank()) {
            return java.time.ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(zone);
        } catch (java.time.DateTimeException unknown) {
            throw new IllegalArgumentException("Fuseau horaire inconnu : " + zone, unknown);
        }
    }

    private ActivityDtos.RunnerTotalsResponse toResponse(StatsPeriod period, ZoneId zone,
            RunnerTotals totals) {

        return new ActivityDtos.RunnerTotalsResponse(
                period.name(),
                period.startingAt(clock, zone).orElse(null),
                totals.activityCount(),
                totals.distance().meters(),
                totals.movingTime().toSeconds(),
                totals.elevationGain(),
                totals.byType().stream()
                        .map(byType -> new ActivityDtos.TotalsByType(
                                byType.type(),
                                byType.activityCount(),
                                byType.distance().meters(),
                                byType.movingTime().toSeconds()))
                        .toList());
    }
}
