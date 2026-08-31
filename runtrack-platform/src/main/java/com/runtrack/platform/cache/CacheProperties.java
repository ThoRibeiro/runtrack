package com.runtrack.platform.cache;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Les durées de vie du cache, et la part d'aléa ajoutée à chacune.
 *
 * @param jitterRatio proportion du TTL tirée au hasard et ajoutée à chaque écriture
 */
@ConfigurationProperties(prefix = "runtrack.cache")
public record CacheProperties(
        /**
         * {@code Boolean} et non {@code boolean} : un primitif absent de la configuration
         * vaut {@code false}, ce qui désactivait silencieusement tout le cache. Le défaut
         * d'un réglage doit être la valeur qu'on veut en production.
         */
        Boolean enabled,
        double jitterRatio,
        Duration userTtl,
        Duration socialTtl,
        Duration activitySummaryTtl,
        Duration countersTtl,
        Duration shareTokenTtl,
        Duration feedHeadTtl,
        Duration recomputeLock,
        Duration recomputeWait) {

    public CacheProperties {
        enabled = enabled == null || enabled;
        jitterRatio = jitterRatio <= 0 ? 0.2 : jitterRatio;
        userTtl = userTtl == null ? Duration.ofMinutes(10) : userTtl;
        socialTtl = socialTtl == null ? Duration.ofMinutes(5) : socialTtl;
        activitySummaryTtl = activitySummaryTtl == null ? Duration.ofHours(24) : activitySummaryTtl;
        countersTtl = countersTtl == null ? Duration.ofMinutes(1) : countersTtl;
        shareTokenTtl = shareTokenTtl == null ? Duration.ofMinutes(15) : shareTokenTtl;
        feedHeadTtl = feedHeadTtl == null ? Duration.ofSeconds(30) : feedHeadTtl;
        // Le verrou de recalcul doit survivre à la requête la plus lente qu'il protège, et
        // expirer bien avant le TTL de l'entrée : un verrou trop long fige, trop court ne sert
        // à rien.
        recomputeLock = recomputeLock == null ? Duration.ofSeconds(5) : recomputeLock;
        // Ce que les perdants laissent au gagnant avant de relire. Assez pour une requête
        // ordinaire, assez court pour ne pas se voir sur une page.
        recomputeWait = recomputeWait == null ? Duration.ofMillis(50) : recomputeWait;
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }
}
