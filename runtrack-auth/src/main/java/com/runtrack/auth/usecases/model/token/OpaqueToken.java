package com.runtrack.auth.usecases.model.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.random.RandomGenerator;

/**
 * Un secret opaque de 256 bits et son empreinte.
 *
 * <p>Empreinte SHA-256, et non Argon2 : les deux ne protègent pas la même chose. Un mot de
 * passe est court et choisi par un humain, donc devinable — il faut un hachage lent. Un
 * jeton tiré de 256 bits d'aléa n'est pas devinable, quel que soit le temps qu'on y passe ;
 * le hacher lentement ne protégerait rien et rendrait chaque rafraîchissement coûteux.
 * Ce qu'on veut ici, c'est seulement qu'une fuite de la base ne livre pas de jeton
 * utilisable.
 */
public record OpaqueToken(String secret, String hash) {

    public static final int SECRET_BYTES = 32;

    public OpaqueToken {
        if (secret == null || secret.isBlank() || hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("Jeton opaque incomplet");
        }
    }

    public static OpaqueToken generate(RandomGenerator random) {
        var bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new OpaqueToken(secret, hashOf(secret));
    }

    public static String hashOf(String secret) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 doit être disponible sur toute JVM", e);
        }
    }
}
