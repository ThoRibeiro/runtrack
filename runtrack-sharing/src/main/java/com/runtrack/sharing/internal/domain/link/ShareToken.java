package com.runtrack.sharing.internal.domain.link;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.random.RandomGenerator;

/**
 * Le jeton d'un lien de partage : 256 bits tirés au sort, rendus une seule fois.
 *
 * <p>Il n'est <b>jamais</b> stocké en clair. Ce qui va en base est son empreinte, exactement comme
 * pour un mot de passe et pour la même raison : une fuite de la base ne doit pas ouvrir les
 * courses que des gens ont partagées avec trois personnes.
 *
 * <p>SHA-256 et non Argon2, en revanche. Un secret de 256 bits tiré au hasard n'a ni dictionnaire
 * ni motif à deviner ; un hachage lent y protégerait d'une attaque qui n'existe pas, tout en
 * coûtant à chaque ouverture de lien — c'est-à-dire sur le chemin le plus chaud du module.
 */
public record ShareToken(String value) {

    /** 32 octets : le §3 demande 256 bits, et l'encodage URL les rend en 43 caractères. */
    public static final int BYTES = 32;

    public ShareToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Jeton de partage vide");
        }
    }

    public static ShareToken generate(RandomGenerator random) {
        byte[] secret = new byte[BYTES];
        random.nextBytes(secret);
        return new ShareToken(Base64.getUrlEncoder().withoutPadding().encodeToString(secret));
    }

    public String hash() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 est requis par la plateforme Java", impossible);
        }
    }
}
