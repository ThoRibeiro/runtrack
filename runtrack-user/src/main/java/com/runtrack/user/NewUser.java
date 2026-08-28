package com.runtrack.user;

/** Ce qu'il faut pour ouvrir un compte. Le mot de passe n'y figure pas : il relève d'{@code auth}. */
public record NewUser(String handle, String email, String displayName) {

    public NewUser {
        if (handle == null || email == null || displayName == null) {
            throw new IllegalArgumentException("Inscription incomplète");
        }
    }
}
