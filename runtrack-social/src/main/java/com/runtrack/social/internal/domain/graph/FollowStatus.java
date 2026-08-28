package com.runtrack.social.internal.domain.graph;

/** L'état d'un abonnement. Un compte fermé transforme l'abonnement en demande. */
public enum FollowStatus {

    PENDING,

    ACCEPTED;

    public boolean isAccepted() {
        return this == ACCEPTED;
    }
}
