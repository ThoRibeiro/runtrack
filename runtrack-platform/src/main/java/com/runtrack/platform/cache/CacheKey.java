package com.runtrack.platform.cache;

/**
 * Les clés du cache applicatif, toutes versionnées.
 *
 * <p>Le préfixe {@code cache:v1:} n'est pas décoratif : le jour où la forme d'une valeur
 * change, on passe en {@code v2} et les deux cohabitent le temps que les anciennes
 * expirent. Sans version, un déploiement fait lire à la nouvelle application des valeurs
 * écrites par l'ancienne, et la désérialisation casse en rafale.
 *
 * <p>Les données <em>live</em> n'ont rien à faire ici : elles vivent sous {@code live:*},
 * qui est un bus, pas un cache.
 */
public final class CacheKey {

    public static final String PREFIX = "cache:v1:";

    private CacheKey() {
    }

    public static String userSummary(String userId) {
        return PREFIX + "user:" + userId;
    }

    public static String accountScope(String userId) {
        return PREFIX + "user:" + userId + ":scope";
    }

    public static String followers(String userId) {
        return PREFIX + "followers:" + userId;
    }

    public static String followees(String userId) {
        return PREFIX + "following:" + userId;
    }

    public static String blocks(String userId) {
        return PREFIX + "blocks:" + userId;
    }

    public static String activitySummary(String activityId) {
        return PREFIX + "activity:" + activityId + ":summary";
    }

    public static String activityCounters(String activityId) {
        return PREFIX + "activity:" + activityId + ":counters";
    }

    public static String shareToken(String tokenHash) {
        return PREFIX + "share:" + tokenHash;
    }

    public static String feedHead(String userId) {
        return PREFIX + "feed:" + userId + ":head";
    }
}
