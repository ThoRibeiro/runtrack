package com.runtrack.auth.internal.application.port;

import com.runtrack.auth.internal.domain.token.RefreshToken;
import com.runtrack.shared.id.UserId;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

    Optional<RefreshToken> findByHash(String tokenHash);

    RefreshToken save(RefreshToken token);

    /** Coupe toute une chaîne de rotation d'un coup, quand un jeton volé se represente. */
    void revokeFamily(UUID familyId);

    /** Déconnecte l'utilisateur de partout : changement de mot de passe, suppression de compte. */
    void revokeAllOf(UserId userId);
}
