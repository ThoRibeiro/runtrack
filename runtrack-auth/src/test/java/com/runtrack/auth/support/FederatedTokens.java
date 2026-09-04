package com.runtrack.auth.support;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Des jetons de fournisseur d'identité, forgés sur place.
 *
 * <p><b>Aucun Keycloak dans le build.</b> Le démarrer en Testcontainers et lui provisionner un
 * realm à chaque exécution doublerait la durée du build pour prouver ce que personne ne met en
 * doute — que Keycloak sait signer un jeton. Ce qui se teste ici est l'autre moitié : que
 * l'application accepte un jeton valide, en ouvre le profil, et refuse le reste.
 *
 * <p>La paire est tirée une fois par JVM et ne sort pas des tests. Le décodeur de
 * {@link TestDecoder} valide avec sa moitié publique, donc sans le moindre appel réseau.
 */
public final class FederatedTokens {

    /** L'émetteur que les tests déclarent, et le seul que le décodeur de test accepte. */
    public static final String ISSUER = "https://keycloak.test/realms/runtrack";

    private static final KeyPair REALM_KEY = generate();

    private FederatedTokens() {
    }

    /** Un jeton complet, tel qu'un realm en signe un après connexion. */
    public static String signedFor(UUID subject, String email, boolean emailVerified, String name) {
        var claims = new JWTClaimsSet.Builder()
                .subject(subject.toString())
                .issuer(ISSUER)
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plus(15, ChronoUnit.MINUTES)))
                .claim("email", email)
                .claim("email_verified", emailVerified)
                .claim("name", name)
                .build();
        return sign(claims);
    }

    /** Un jeton auquel il manque l'adresse : un realm qui n'accorde pas le scope {@code email}. */
    public static String signedWithoutEmail(UUID subject) {
        var claims = new JWTClaimsSet.Builder()
                .subject(subject.toString())
                .issuer(ISSUER)
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plus(15, ChronoUnit.MINUTES)))
                .build();
        return sign(claims);
    }

    /** Un jeton d'un autre realm, correctement signé mais par quelqu'un d'autre. */
    public static String signedByAnotherRealm(UUID subject) {
        var claims = new JWTClaimsSet.Builder()
                .subject(subject.toString())
                .issuer("https://keycloak.test/realms/quelquun-dautre")
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plus(15, ChronoUnit.MINUTES)))
                .claim("email", "intrus@example.com")
                .build();
        return sign(claims);
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String sign(JWTClaimsSet claims) {
        try {
            var header = new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build();
            SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(new RSASSASigner((RSAPrivateKey) REALM_KEY.getPrivate()));
            return jwt.serialize();
        } catch (Exception impossible) {
            throw new IllegalStateException("Signature d'un jeton de test impossible", impossible);
        }
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2_048);
            return generator.generateKeyPair();
        } catch (Exception impossible) {
            throw new IllegalStateException("RSA doit être disponible sur toute JVM", impossible);
        }
    }

    /**
     * Le décodeur que voit l'application pendant ces tests : la clé publique du faux realm, et
     * la vérification de l'émetteur, exactement comme en production — moins l'aller-retour vers
     * un JWKS que personne ne sert ici.
     */
    @TestConfiguration(proxyBeanMethods = false)
    public static class TestDecoder {

        @Bean
        @Primary
        JwtDecoder realmJwtDecoder() {
            NimbusJwtDecoder decoder = NimbusJwtDecoder
                    .withPublicKey((RSAPublicKey) REALM_KEY.getPublic())
                    .build();
            decoder.setJwtValidator(
                    org.springframework.security.oauth2.jwt.JwtValidators
                            .createDefaultWithIssuer(ISSUER));
            return decoder;
        }
    }
}
