/**
 * La supervision et la reprise du registre de publications d'événements de Spring Modulith.
 *
 * <p>Le registre est l'outbox transactionnelle du §7 : on ne l'écrit pas, mais on répond de ce
 * qu'il contient. Ce paquet ajoute les trois choses que Modulith laisse à la charge de
 * l'application : réessayer avec un recul croissant, s'arrêter au bout de N tentatives, et
 * rendre visible ce qui n'a jamais abouti.
 */
package com.runtrack.platform.events;
