package com.runtrack.user.usecases.model.profile;

/** L'état d'un compte. Seul un compte {@link #ACTIVE} peut agir. */
public enum AccountStatus {

    /** Inscrit, mais l'adresse e-mail n'a pas encore été confirmée. */
    PENDING_VERIFICATION,

    ACTIVE,

    /** Suspendu par la modération : l'utilisateur ne peut plus agir, ses données restent. */
    SUSPENDED,

    /** Supprimé et anonymisé. Conservé pour ne pas casser les références des courses. */
    DELETED;

    public boolean canAct() {
        return this == ACTIVE;
    }
}
