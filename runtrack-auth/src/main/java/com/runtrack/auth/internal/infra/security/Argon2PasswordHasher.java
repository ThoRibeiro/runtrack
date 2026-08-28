package com.runtrack.auth.internal.infra.security;

import com.runtrack.auth.internal.application.port.PasswordHasher;
import com.runtrack.auth.internal.domain.credential.Password;
import com.runtrack.auth.internal.domain.credential.PasswordHash;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Argon2id, avec des paramètres explicites plutôt que ceux par défaut.
 *
 * <p>Les valeurs suivent la recommandation OWASP « 19 MiB, 2 itérations, parallélisme 1 »,
 * qui vise environ 50 ms par hachage sur un serveur ordinaire. C'est le compromis à tenir :
 * assez lent pour qu'une attaque hors-ligne sur une base volée coûte cher, assez rapide
 * pour qu'une rafale de connexions ne devienne pas elle-même un déni de service.
 *
 * <p>Le sel et les paramètres sont encodés dans l'empreinte : augmenter le coût plus tard
 * ne casse pas les mots de passe déjà enregistrés.
 */
@Component
class Argon2PasswordHasher implements PasswordHasher {

    private static final int SALT_LENGTH_BYTES = 16;
    private static final int HASH_LENGTH_BYTES = 32;
    private static final int PARALLELISM = 1;
    private static final int MEMORY_KIB = 19 * 1_024;
    private static final int ITERATIONS = 2;

    private final Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(
            SALT_LENGTH_BYTES, HASH_LENGTH_BYTES, PARALLELISM, MEMORY_KIB, ITERATIONS);

    @Override
    public PasswordHash hash(Password password) {
        return new PasswordHash(encoder.encode(password.value()));
    }

    @Override
    public boolean matches(Password candidate, PasswordHash hash) {
        return encoder.matches(candidate.value(), hash.value());
    }
}
