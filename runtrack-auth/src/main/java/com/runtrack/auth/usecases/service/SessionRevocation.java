package com.runtrack.auth.usecases.service;

import com.runtrack.auth.usecases.port.RefreshTokenRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * La révocation défensive d'une famille de jetons, dans sa <em>propre</em> transaction.
 *
 * <p>Sans cela, la détection de vol ne servirait à rien : le cas d'usage révoque la famille
 * puis lève une exception pour refuser la requête, et ce rejet annule la transaction — donc
 * la révocation avec. Le voleur garderait une chaîne intacte, et rien ne l'aurait signalé.
 *
 * <p>C'est un composant distinct parce qu'un appel de méthode interne ne traverse pas le
 * proxy Spring : {@code Propagation.REQUIRES_NEW} sur une méthode privée n'aurait aucun
 * effet.
 */
@Service
public class SessionRevocation {

    private final RefreshTokenRepository refreshTokens;

    public SessionRevocation(RefreshTokenRepository refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeFamily(UUID familyId) {
        refreshTokens.revokeFamily(familyId);
    }
}
