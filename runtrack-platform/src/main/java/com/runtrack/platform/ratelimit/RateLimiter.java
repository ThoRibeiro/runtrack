package com.runtrack.platform.ratelimit;

import java.time.Duration;

/**
 * « Cet appelant a-t-il encore droit à une tentative ? »
 *
 * <p>Fenêtre fixe, et c'est un choix : une fenêtre glissante exacte demanderait de garder chaque
 * horodatage, là où un compteur qui expire tient en deux commandes. Le défaut connu de la fenêtre
 * fixe — deux fois le quota à cheval sur la bascule — est sans conséquence ici, où l'on cherche à
 * arrêter un script qui martèle, pas à facturer à l'appel près.
 */
public interface RateLimiter {

    /**
     * Compte l'appel et dit s'il passe.
     *
     * <p>Compte <b>même quand il refuse</b> : sans cela, un attaquant qui continue de frapper
     * verrait sa fenêtre expirer et repartir, alors qu'il n'a jamais cessé.
     *
     * @param key ce qu'on limite — une adresse, un compte, une course
     * @return {@code true} si l'appel est dans le quota
     */
    boolean tryAcquire(String key, int limit, Duration window);
}
