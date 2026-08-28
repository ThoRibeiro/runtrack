package com.runtrack.auth.internal.domain.credential;

/** L'empreinte d'un mot de passe. Seule forme qui atteint la base de données. */
public record PasswordHash(String value) {

    public PasswordHash {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Empreinte de mot de passe absente");
        }
    }
}
