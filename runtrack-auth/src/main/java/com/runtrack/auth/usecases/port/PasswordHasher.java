package com.runtrack.auth.usecases.port;

import com.runtrack.auth.usecases.model.credential.Password;
import com.runtrack.auth.usecases.model.credential.PasswordHash;

/**
 * Le hachage des mots de passe. Port sortant, donc l'algorithme reste dans
 * {@code infra} : les cas d'usage se testent sans faire tourner Argon2, dont le coût est
 * précisément d'être lent.
 */
public interface PasswordHasher {

    PasswordHash hash(Password password);

    boolean matches(Password candidate, PasswordHash hash);
}
