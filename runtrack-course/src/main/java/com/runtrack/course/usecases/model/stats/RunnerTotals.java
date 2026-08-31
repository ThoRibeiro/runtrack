package com.runtrack.course.usecases.model.stats;

import com.runtrack.shared.measure.Distance;
import java.time.Duration;
import java.util.List;

/**
 * Le bilan d'un coureur sur une période.
 *
 * <p>Seules les courses <b>terminées</b> y entrent : une course en cours n'a pas de total, et une
 * course abandonnée n'en a plus.
 *
 * <p>Le détail par type est rendu à côté du total, pas à la place : quelqu'un qui court et roule
 * veut voir les deux, et recomposer le total côté client inviterait chaque écran à le faire
 * différemment.
 */
public record RunnerTotals(
        long activityCount,
        Distance distance,
        Duration movingTime,
        double elevationGain,
        List<ByType> byType) {

    public RunnerTotals {
        byType = List.copyOf(byType);
    }

    public static RunnerTotals empty() {
        return new RunnerTotals(0, Distance.ZERO, Duration.ZERO, 0, List.of());
    }

    public record ByType(String type, long activityCount, Distance distance, Duration movingTime) {
    }
}
