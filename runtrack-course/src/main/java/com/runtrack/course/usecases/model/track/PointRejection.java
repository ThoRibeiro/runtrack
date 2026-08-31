package com.runtrack.course.usecases.model.track;

/**
 * Pourquoi un point a été écarté. Renvoyé au client, qui peut ainsi distinguer un doublon
 * bénin — le rejeu attendu de son tampon — d'un capteur qui déraille.
 */
public enum PointRejection {

    /** Précision annoncée trop mauvaise : le point situe le coureur à un pâté de maisons près. */
    ACCURACY_TOO_LOW,

    /** La vitesse impliquée depuis le point précédent est impossible pour ce type d'activité. */
    IMPLAUSIBLE_SPEED,

    /** Horodatage postérieur à l'heure serveur, même après correction de la dérive. */
    TIMESTAMP_IN_FUTURE,

    /** Horodatage antérieur au démarrage de la course. */
    TIMESTAMP_BEFORE_START,

    /** Numéro de séquence déjà appliqué : c'est un rejeu, et il est sans effet. */
    DUPLICATE_SEQUENCE
}
