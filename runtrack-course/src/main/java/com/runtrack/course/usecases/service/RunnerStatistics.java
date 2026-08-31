package com.runtrack.course.usecases.service;

import com.runtrack.course.usecases.model.stats.RunnerTotals;
import com.runtrack.course.usecases.model.stats.StatsPeriod;
import com.runtrack.course.usecases.port.RunnerTotalsRepository;
import com.runtrack.shared.id.UserId;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Le bilan qu'un coureur consulte sur lui-même.
 *
 * <p>Sur <b>lui-même</b> uniquement, et c'est délibéré : les totaux d'un tiers agrègent des courses
 * dont certaines peuvent être privées, et l'agrégat les révélerait par la bande — un total qui
 * bouge dit qu'une course a eu lieu. Le §8 ne prévoit d'ailleurs l'endpoint que sur {@code /me}.
 *
 * <p>Ce service vit dans {@code course} et non dans {@code user}, bien que l'URL commence par
 * {@code /users/me} : les courses appartiennent à {@code course}, le §10 interdit qu'un autre
 * module lise ses tables, et faire dépendre {@code user} de {@code course} fermerait un cycle —
 * {@code course} dépend déjà de {@code user}. Le même arrangement sert déjà
 * {@code /users/me/devices}, porté par {@code notification}.
 */
@Service
public class RunnerStatistics {

    private final RunnerTotalsRepository totals;
    private final Clock clock;

    public RunnerStatistics(RunnerTotalsRepository totals, Clock clock) {
        this.totals = totals;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RunnerTotals of(UserId runnerId, StatsPeriod period, ZoneId zone) {
        return totals.totalsOf(runnerId, period.startingAt(clock, zone));
    }
}
