package com.runtrack.platform.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Les quotas du §9. Des réglages d'exploitation, donc configurables — contrairement aux règles
 * métier, qui restent dans le code.
 *
 * <p><b>Le quota par adresse IP est bien plus large que celui par compte</b>, et l'écart est
 * délibéré. Une adresse est <em>partagée</em> : un foyer, un bureau, un opérateur mobile derrière
 * un NAT. La régler comme un compte reviendrait à verrouiller tout un immeuble parce que dix
 * personnes s'y sont connectées. Ce que le compteur par IP arrête, c'est le balayage de milliers
 * de comptes depuis une machine ; ce que celui par compte arrête, c'est le forçage d'un mot de
 * passe en changeant d'adresse. Les deux sont nécessaires, à des échelles différentes.
 *
 * <p>Types objets et non primitifs : une propriété absente vaudrait {@code 0} sans que rien ne le
 * signale, et un quota de zéro refuse tout.
 */
@ConfigurationProperties("runtrack.ratelimit")
public record RateLimitProperties(
        Integer loginPerIp,
        Integer loginPerAccount,
        Duration loginWindow,
        Integer ingestBatchesPerActivity,
        Duration ingestWindow,
        Integer commentsPerAuthor,
        Duration commentsWindow) {

    public RateLimitProperties {
        loginPerIp = loginPerIp == null ? 200 : loginPerIp;
        loginPerAccount = loginPerAccount == null ? 10 : loginPerAccount;
        loginWindow = loginWindow == null ? Duration.ofMinutes(15) : loginWindow;
        // Cinq fois le rythme nominal d'un client — un envoi toutes les cinq à dix secondes —,
        // de quoi absorber le rejeu d'un tampon après une coupure réseau.
        ingestBatchesPerActivity = ingestBatchesPerActivity == null ? 60 : ingestBatchesPerActivity;
        ingestWindow = ingestWindow == null ? Duration.ofMinutes(1) : ingestWindow;
        commentsPerAuthor = commentsPerAuthor == null ? 20 : commentsPerAuthor;
        commentsWindow = commentsWindow == null ? Duration.ofHours(1) : commentsWindow;
    }
}
