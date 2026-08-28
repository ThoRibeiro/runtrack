package com.runtrack.shared;

import java.util.Optional;

/**
 * Qui regarde. Vit dans le noyau partagé, et non dans {@code course}, pour que
 * {@code course} n'ait jamais à connaître {@code sharing} : le filtre de sécurité résout
 * le jeton de partage en amont et pose un {@link ShareLinkHolder} déjà porteur de
 * l'identifiant de course. C'est ce qui garde le graphe des modules acyclique.
 */
public sealed interface Viewer {

    /** L'utilisateur authentifié, s'il y en a un. */
    Optional<UserId> userId();

    /** Un utilisateur connecté. */
    record AuthenticatedUser(UserId id) implements Viewer {

        public AuthenticatedUser {
            if (id == null) {
                throw new IllegalArgumentException("AuthenticatedUser sans identifiant");
            }
        }

        @Override
        public Optional<UserId> userId() {
            return Optional.of(id);
        }
    }

    /** Le porteur d'un lien de partage valide, pour une course précise et elle seule. */
    record ShareLinkHolder(ActivityId activityId) implements Viewer {

        public ShareLinkHolder {
            if (activityId == null) {
                throw new IllegalArgumentException("ShareLinkHolder sans course");
            }
        }

        @Override
        public Optional<UserId> userId() {
            return Optional.empty();
        }

        public boolean grantsAccessTo(ActivityId candidate) {
            return activityId.equals(candidate);
        }
    }

    /** Un visiteur non authentifié et sans lien de partage. */
    record Anonymous() implements Viewer {

        public static final Anonymous INSTANCE = new Anonymous();

        @Override
        public Optional<UserId> userId() {
            return Optional.empty();
        }
    }
}
