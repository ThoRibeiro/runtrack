package com.runtrack.auth.infrastructure.security;

import com.runtrack.auth.usecases.port.AccessTokenIssuer;
import com.runtrack.shared.id.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Émission des jetons d'accès, signés en RS256.
 *
 * <p>Signature asymétrique et non HMAC : la clé publique suffit à vérifier un jeton, donc
 * un service qui ne fait que lire n'a jamais besoin du secret de signature. Avec HS256,
 * tout vérificateur peut aussi forger.
 *
 * <p>Durée courte — quinze minutes par défaut — parce qu'un jeton d'accès n'est pas
 * révocable : la seule limite à un jeton volé est son expiration. C'est le rafraîchissement
 * rotatif qui porte la révocation.
 */
@Component
class JwtAccessTokenIssuer implements AccessTokenIssuer {

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    JwtAccessTokenIssuer(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String issueFor(UserId userId) {
        Instant now = clock.instant();
        var claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenLifetime()))
                .subject(userId.toString())
                .build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    @Override
    public Duration lifetime() {
        return properties.accessTokenLifetime();
    }
}
