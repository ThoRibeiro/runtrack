package com.runtrack.course.internal.application.port;

import com.runtrack.course.internal.domain.live.LiveEvent;
import com.runtrack.shared.id.ActivityId;
import java.util.List;

/**
 * Le port sortant du direct : ce qu'une course émet part par ici.
 *
 * <p>Le cas d'usage ne sait rien du Stream Dragonfly ni du SSE. Il sait qu'une position a été
 * enregistrée et le dit ; où cela atterrit est le problème de l'adaptateur.
 *
 * <p>Aucune de ces méthodes ne doit faire échouer l'appelant. Le direct est un confort :
 * une course dont les points sont bien enregistrés mais que personne ne suit en temps réel
 * reste une course réussie. L'inverse ne l'est pas.
 */
public interface LiveActivityPublisher {

    void publish(ActivityId activityId, List<LiveEvent> events);

    /**
     * La course est terminée : plus rien ne sera publié.
     *
     * <p>L'historique n'est pas effacé sur-le-champ. Un spectateur en cours de reconnexion
     * tient un {@code Last-Event-ID} qu'il va vouloir rejouer, et un client mobile qui repasse
     * du métro au réseau met quelques secondes : couper immédiatement lui ferait manquer la
     * fin de la course qu'il regardait. Les clés expirent donc peu après.
     */
    void closeStream(ActivityId activityId);
}
