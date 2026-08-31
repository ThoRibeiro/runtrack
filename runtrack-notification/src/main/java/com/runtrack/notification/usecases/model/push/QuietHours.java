package com.runtrack.notification.usecases.model.push;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * La plage pendant laquelle le téléphone doit rester silencieux.
 *
 * <p>Elle est exprimée dans le fuseau du destinataire, et c'est essentiel : « pas avant 7 h »
 * n'a de sens que là où il se trouve. Stocker une heure sans fuseau réveillerait un coureur de
 * Nouméa à ce qui est 7 h à Paris.
 *
 * <p>La plage traverse minuit dans le cas usuel — 22 h à 7 h — donc la comparaison ne peut pas
 * être un simple encadrement. Quand le début est postérieur à la fin, la plage est l'union de
 * « après le début » et « avant la fin ».
 */
public record QuietHours(LocalTime from, LocalTime to, ZoneId zone) {

    public QuietHours {
        if (from == null || to == null || zone == null) {
            throw new IllegalArgumentException("Heures calmes incomplètes");
        }
        if (from.equals(to)) {
            // Une plage vide et une plage de 24 h s'écriraient pareil : refuser lève l'ambiguïté
            // plutôt que de choisir au hasard laquelle des deux l'utilisateur voulait.
            throw new IllegalArgumentException("Heures calmes de durée nulle ou totale : "
                    + "utiliser l'absence de plage pour ne rien couper");
        }
    }

    public boolean covers(Instant moment) {
        LocalTime local = moment.atZone(zone).toLocalTime();
        return from.isBefore(to)
                ? !local.isBefore(from) && local.isBefore(to)
                : !local.isBefore(from) || local.isBefore(to);
    }
}
