package com.runtrack.auth.infrastructure.security;

import com.runtrack.shared.id.UserId;
import com.runtrack.user.FederatedProfile;
import com.runtrack.user.UserApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Ouvre le profil d'un compte du realm à sa première requête.
 *
 * <p>Sans inscription maison, plus rien ne crée le profil : le fournisseur d'identité connaît
 * la personne, l'application ne la connaît pas encore. C'est le pendant exact de ce que faisait
 * {@code AuthController.signUp} — et c'est pour cela que ce code vit dans {@code auth}, seul
 * module à voir le jeton, et déjà seul à appeler {@code UserApi.register}.
 *
 * <p><b>Le coût par requête est une lecture de cache, pas une requête SQL.</b> L'existence se
 * teste par {@code summary}, que le décorateur de {@code user} sert depuis Dragonfly, et non par
 * {@code exists}, qui irait en base à chaque appel — l'ingestion de points passe ici une fois par
 * seconde et par coureur.
 *
 * <p>Un jeton sans adresse e-mail n'ouvre pas de profil : le realm doit accorder le scope
 * {@code email}. La requête continue authentifiée, et c'est la lecture du profil qui répondra
 * « inconnu » — un refus ici transformerait une configuration de realm incomplète en panne
 * d'authentification difficile à lire.
 */
@Component
@ConditionalOnProperty(name = "runtrack.auth.provider", havingValue = "keycloak")
class FederatedAccountProvisioning {

    private static final Logger LOG = LoggerFactory.getLogger(FederatedAccountProvisioning.class);

    private static final String EMAIL = "email";
    private static final String EMAIL_VERIFIED = "email_verified";
    private static final String NAME = "name";

    private final UserApi users;

    FederatedAccountProvisioning(UserApi users) {
        this.users = users;
    }

    void ensureProfileOf(Jwt jwt, UserId id) {
        if (users.summary(id).isPresent()) {
            return;
        }
        String email = jwt.getClaimAsString(EMAIL);
        if (email == null || email.isBlank()) {
            LOG.warn("Jeton sans adresse e-mail : aucun profil ouvert pour {}", id);
            return;
        }
        if (users.ensureProfile(id, new FederatedProfile(
                email, jwt.getClaimAsString(NAME), Boolean.TRUE.equals(jwt.getClaim(EMAIL_VERIFIED))))) {

            LOG.info("Profil ouvert pour le compte fédéré {}", id);
        }
    }
}
