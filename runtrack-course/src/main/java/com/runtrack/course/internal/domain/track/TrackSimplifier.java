package com.runtrack.course.internal.domain.track;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * La simplification de Douglas-Peucker, pour l'affichage.
 *
 * <p>Une sortie de trois heures compte dix mille points, dont l'immense majorité tombe sur une
 * ligne droite : les envoyer tous coûte de la bande passante et du temps de rendu pour des pixels
 * identiques. L'algorithme ne garde que les points qui changent réellement la forme du tracé.
 *
 * <p>Il ne remplace jamais les points bruts : ceux-ci restent la source des statistiques et de
 * l'export. Ce qui est simplifié n'est que la trace <em>dessinée</em>.
 *
 * <p><b>Itératif, et non récursif.</b> La version récursive du manuel dégénère sur une trace déjà
 * presque droite — le cas le plus courant — où la pile atteint la profondeur du nombre de points.
 * Dix mille points suffisent à faire déborder la pile d'un fil virtuel, dont la pile est plus
 * modeste que celle d'un fil de plateforme.
 */
public final class TrackSimplifier {

    /**
     * Cinq mètres.
     *
     * <p>En deçà de la précision d'un GPS de téléphone : ce qui est écarté à cette tolérance est du
     * bruit, pas du relief. Et à l'échelle où une carte affiche une course entière, cinq mètres
     * pèsent moins d'un pixel.
     */
    public static final double TOLERANCE_METERS = 5;

    private TrackSimplifier() {
    }

    public static List<TrackPoint> simplify(List<TrackPoint> points, double toleranceMeters) {
        if (points.size() < 3) {
            return List.copyOf(points);
        }
        boolean[] kept = new boolean[points.size()];
        kept[0] = true;
        kept[points.size() - 1] = true;

        Deque<int[]> segments = new ArrayDeque<>();
        segments.push(new int[] {0, points.size() - 1});

        while (!segments.isEmpty()) {
            int[] segment = segments.pop();
            int start = segment[0];
            int end = segment[1];

            int farthest = -1;
            double greatest = toleranceMeters;
            for (int index = start + 1; index < end; index++) {
                double deviation = distanceToSegment(
                        points.get(index), points.get(start), points.get(end));
                if (deviation > greatest) {
                    greatest = deviation;
                    farthest = index;
                }
            }
            if (farthest < 0) {
                // Tout ce qui sépare les deux bornes tient dans la tolérance : la ligne droite suffit.
                continue;
            }
            kept[farthest] = true;
            segments.push(new int[] {start, farthest});
            segments.push(new int[] {farthest, end});
        }

        var simplified = new ArrayList<TrackPoint>();
        for (int index = 0; index < points.size(); index++) {
            if (kept[index]) {
                simplified.add(points.get(index));
            }
        }
        return List.copyOf(simplified);
    }

    /**
     * L'écart d'un point à la corde qui joint les deux bornes.
     *
     * <p>Calculé en mètres via la projection locale de {@link DistanceCalculator} plutôt qu'en
     * degrés : un degré de longitude vaut 111 km à l'équateur et 55 km à Lille, si bien qu'une
     * tolérance exprimée en degrés simplifierait deux fois plus au nord qu'au sud.
     */
    private static double distanceToSegment(TrackPoint point, TrackPoint start, TrackPoint end) {
        double alongLength = DistanceCalculator.between(start.position(), end.position()).meters();
        if (alongLength == 0) {
            return DistanceCalculator.between(start.position(), point.position()).meters();
        }
        double toStart = DistanceCalculator.between(start.position(), point.position()).meters();
        double toEnd = DistanceCalculator.between(end.position(), point.position()).meters();

        // Formule de Héron : l'aire du triangle donne la hauteur, qui est l'écart cherché.
        double semiPerimeter = (alongLength + toStart + toEnd) / 2;
        double squaredArea = semiPerimeter * (semiPerimeter - alongLength)
                * (semiPerimeter - toStart) * (semiPerimeter - toEnd);
        if (squaredArea <= 0) {
            // Trois points alignés, ou des arrondis qui rendent l'aire négative : aucun écart.
            return 0;
        }
        return 2 * Math.sqrt(squaredArea) / alongLength;
    }
}
