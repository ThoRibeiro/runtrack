package com.runtrack.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.runtrack.auth.usecases.fixture.AuthDoubles;
import com.runtrack.shared.id.UserId;
import com.runtrack.user.UserSummary;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/** L'accueil d'un compte du realm, à sa première requête. */
class FederatedAccountProvisioningTest {

    private static final UserId MARIE = UserId.of("0198c4d2-7f31-7a42-9c55-1b2c3d4e5f60");

    private final AuthDoubles.Users users = new AuthDoubles.Users();
    private final FederatedAccountProvisioning provisioning = new FederatedAccountProvisioning(users);

    private static Jwt tokenOf(String email, Object emailVerified, String name) {
        Jwt.Builder builder = Jwt.withTokenValue("jeton")
                .header("alg", "RS256")
                .subject(MARIE.toString())
                .claim("name", name);
        if (email != null) {
            builder.claim("email", email);
        }
        if (emailVerified != null) {
            builder.claim("email_verified", emailVerified);
        }
        return builder.build();
    }

    @Test
    void opensAProfileForAnIdentityTheApplicationHasNeverSeen() {
        provisioning.ensureProfileOf(tokenOf("marie@example.com", true, "Marie"), MARIE);

        assertThat(users.provisioned).isNotNull();
        assertThat(users.provisioned.email()).isEqualTo("marie@example.com");
        assertThat(users.provisioned.displayName()).isEqualTo("Marie");
        assertThat(users.provisioned.emailVerified()).isTrue();
    }

    /**
     * Le chemin de toutes les requêtes suivantes : le profil est connu, et la question se règle
     * sur le cache de {@code summary} sans que rien ne soit écrit.
     */
    @Test
    void doesNothingWhenTheProfileIsAlreadyThere() {
        users.known = new UserSummary(MARIE, "marie", "Marie", Optional.empty());

        provisioning.ensureProfileOf(tokenOf("marie@example.com", true, "Marie"), MARIE);

        assertThat(users.provisioned).isNull();
    }

    /**
     * Un realm qui n'accorde pas le scope {@code email} ne doit pas faire tomber
     * l'authentification : la requête continue, et c'est la lecture du profil qui dira
     * « inconnu ».
     */
    @Test
    void opensNothingWithoutAnAddressToOpenItWith() {
        provisioning.ensureProfileOf(tokenOf(null, true, "Marie"), MARIE);

        assertThat(users.provisioned).isNull();
    }

    /** Une adresse non confirmée par le fournisseur reste non confirmée chez nous. */
    @Test
    void carriesTheProvidersVerdictOnTheAddress() {
        provisioning.ensureProfileOf(tokenOf("marie@example.com", null, "Marie"), MARIE);

        assertThat(users.provisioned.emailVerified()).isFalse();
    }
}
