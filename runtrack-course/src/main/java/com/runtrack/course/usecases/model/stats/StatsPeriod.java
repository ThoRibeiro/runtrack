package com.runtrack.course.usecases.model.stats;

import java.time.Clock;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneId;
import java.util.Optional;

/**
 * La fenêtre sur laquelle un utilisateur regarde son propre bilan.
 *
 * <p>Les bornes sont <b>calendaires</b> et non glissantes : « ce mois-ci » commence le premier du
 * mois, pas trente jours en arrière. C'est ce qu'un coureur entend quand il regarde son total
 * mensuel, et une fenêtre glissante donnerait un chiffre qui baisse alors qu'il n'a rien couru de
 * moins — simplement parce qu'une sortie ancienne est sortie de la fenêtre.
 *
 * <p>Le fuseau est celui de l'appelant, à défaut UTC : la limite d'une semaine n'a de sens que
 * quelque part, et découper à minuit UTC ferait basculer une sortie du dimanche soir dans la
 * semaine suivante pour la moitié de la planète.
 */
public enum StatsPeriod {

    WEEK(Period.ofWeeks(1)),
    MONTH(Period.ofMonths(1)),
    YEAR(Period.ofYears(1)),
    /** Depuis toujours : le seul cas sans borne inférieure. */
    ALL(null);

    private final Period length;

    StatsPeriod(Period length) {
        this.length = length;
    }

    /** @return la borne inférieure, absente pour {@link #ALL} */
    public Optional<Instant> startingAt(Clock clock, ZoneId zone) {
        if (length == null) {
            return Optional.empty();
        }
        var today = clock.instant().atZone(zone).toLocalDate();
        var from = switch (this) {
            case WEEK -> today.with(java.time.temporal.WeekFields.ISO.dayOfWeek(), 1);
            case MONTH -> today.withDayOfMonth(1);
            case YEAR -> today.withDayOfYear(1);
            case ALL -> today;
        };
        return Optional.of(from.atStartOfDay(zone).toInstant());
    }
}
