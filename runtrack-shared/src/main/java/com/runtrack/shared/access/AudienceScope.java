package com.runtrack.shared.access;

/**
 * À qui une chose est visible. Un seul type pour les deux niveaux — la visibilité du
 * compte et celle de la course — au lieu de deux énumérations aux valeurs identiques.
 *
 * <p>Deux énumérations jumelles se confondent : on passe la visibilité du compte là où
 * le code attend celle de la course, ça compile, et une course privée devient publique.
 * Avec un seul type, la composition passe par {@link #mostRestrictive(AudienceScope)},
 * qui est <em>commutative</em> : intervertir les deux arguments ne change pas le résultat.
 * Le bug n'est pas seulement détecté, il n'existe plus.
 */
public enum AudienceScope {

    /** Visible de tous, y compris sans être authentifié. */
    PUBLIC(0),

    /** Visible des seuls abonnés acceptés. */
    FOLLOWERS(1),

    /** Visible du seul propriétaire. */
    PRIVATE(2);

    private final int restrictiveness;

    AudienceScope(int restrictiveness) {
        this.restrictiveness = restrictiveness;
    }

    /**
     * La plus fermée des deux portées. Une course PUBLIC sur un compte PRIVATE est
     * privée : c'est le compte qui gagne, jamais l'inverse.
     */
    public AudienceScope mostRestrictive(AudienceScope other) {
        return restrictiveness >= other.restrictiveness ? this : other;
    }

    public boolean isAtLeastAsOpenAs(AudienceScope other) {
        return restrictiveness <= other.restrictiveness;
    }
}
