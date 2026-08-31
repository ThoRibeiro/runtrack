package com.runtrack.course.usecases.model.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** Les bornes du bilan : calendaires, et dans le fuseau de celui qui regarde. */
class StatsPeriodTest {

    /** Un mercredi, 14 h à Paris. */
    private static final Instant WEDNESDAY = Instant.parse("2026-08-19T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(WEDNESDAY, ZoneOffset.UTC);
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    @Test
    void theWeekStartsOnMondayNotSevenDaysAgo() {
        Instant start = StatsPeriod.WEEK.startingAt(CLOCK, PARIS).orElseThrow();

        // Lundi 17 août, minuit à Paris — soit 22 h UTC le dimanche 16.
        assertThat(start).isEqualTo(Instant.parse("2026-08-16T22:00:00Z"));
    }

    @Test
    void theMonthStartsOnTheFirst() {
        assertThat(StatsPeriod.MONTH.startingAt(CLOCK, PARIS))
                .contains(Instant.parse("2026-07-31T22:00:00Z"));
    }

    @Test
    void theYearStartsOnTheFirstOfJanuary() {
        assertThat(StatsPeriod.YEAR.startingAt(CLOCK, PARIS))
                .contains(Instant.parse("2025-12-31T23:00:00Z"));
    }

    /** « Depuis toujours » est le seul cas sans borne : une date de début serait inventée. */
    @Test
    void allTimeHasNoLowerBound() {
        assertThat(StatsPeriod.ALL.startingAt(CLOCK, PARIS)).isEmpty();
    }

    /**
     * Le fuseau déplace réellement la borne.
     *
     * <p>Sans lui, une sortie du dimanche soir basculerait dans la semaine suivante pour la moitié
     * de la planète — et le total du lundi matin serait faux d'une course.
     */
    @Test
    void theBoundaryMovesWithTheTimeZone() {
        Instant inParis = StatsPeriod.WEEK.startingAt(CLOCK, PARIS).orElseThrow();
        Instant inNoumea = StatsPeriod.WEEK.startingAt(CLOCK, ZoneId.of("Pacific/Noumea"))
                .orElseThrow();

        assertThat(inNoumea).isBefore(inParis);
    }
}
