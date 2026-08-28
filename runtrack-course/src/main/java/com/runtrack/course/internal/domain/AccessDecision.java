package com.runtrack.course.internal.domain;

/**
 * L'issue d'un contrôle de visibilité, et sa raison. Le motif du refus ne sort pas tel
 * quel de l'API — dire « ce compte vous a bloqué » renseigne l'appelant — mais il rend
 * les tests lisibles et les journaux exploitables.
 */
public enum AccessDecision {

    GRANTED,

    /** Un blocage, dans un sens ou dans l'autre. Prime sur tout, lien de partage compris. */
    DENIED_BLOCKED,

    /** Course réservée aux abonnés, et le lecteur n'en est pas un. */
    DENIED_NOT_A_FOLLOWER,

    /** Course privée : personne d'autre que le propriétaire, sauf lien de partage valide. */
    DENIED_PRIVATE,

    /** Course non publique et lecteur non authentifié. */
    DENIED_ANONYMOUS;

    public boolean isGranted() {
        return this == GRANTED;
    }
}
