package com.runtrack.course.infrastructure.endpoint;

import com.runtrack.shared.access.Viewer;
import com.runtrack.shared.error.ForbiddenException;
import com.runtrack.shared.id.UserId;

/** Le passage du principal Spring Security au {@link Viewer} du domaine. */
final class Principals {

    private Principals() {
    }

    /** Un lecteur absent est un anonyme, pas une erreur : certaines courses sont publiques. */
    static Viewer asViewer(Viewer viewer) {
        return viewer == null ? Viewer.Anonymous.INSTANCE : viewer;
    }

    /** Agir sur une course demande un compte : ni l'anonyme ni un lien de partage ne le peuvent. */
    static UserId requireUser(Viewer viewer) {
        if (viewer == null) {
            throw new ForbiddenException("AUTHENTICATION_REQUIRED", "Cette action demande d'être connecté");
        }
        return viewer.userId().orElseThrow(() -> new ForbiddenException(
                "AUTHENTICATION_REQUIRED", "Un lien de partage ne permet pas d'agir sur une course"));
    }
}
