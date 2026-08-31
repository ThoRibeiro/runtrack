package com.runtrack.engagement.usecases.model.interaction;

/**
 * Ce qu'un écran de course affiche à côté du cœur et de la bulle.
 *
 * <p>Les deux ensemble, et non deux lectures séparées : ils sont affichés côte à côte, lus au
 * même moment, et invalidés par les mêmes gestes. Les séparer doublerait les allers-retours et les
 * clés à tenir justes.
 */
public record ActivityCounters(long likes, long comments) {

    public static final ActivityCounters NONE = new ActivityCounters(0, 0);

    public ActivityCounters {
        if (likes < 0 || comments < 0) {
            throw new IllegalArgumentException("Un compteur d'engagement ne descend pas sous zéro");
        }
    }
}
