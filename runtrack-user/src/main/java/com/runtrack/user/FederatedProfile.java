package com.runtrack.user;

/**
 * Ce qu'un fournisseur d'identité sait de quelqu'un, et qui suffit à lui ouvrir un profil.
 *
 * <p>Le pseudo n'y figure pas, et c'est délibéré : il porte une règle du domaine — unique,
 * normalisé, présent dans les URL — qui ne se délègue pas à une console d'administration. Le
 * profil naît donc avec un pseudo provisoire que la personne remplace par
 * {@code PUT /user/v1/me/handle}.
 *
 * @param emailVerified ce que le fournisseur affirme avoir vérifié. Un compte fédéré dont
 *     l'adresse est confirmée n'a aucune raison de repasser par la vérification maison.
 */
public record FederatedProfile(String email, String displayName, boolean emailVerified) {

    public FederatedProfile {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Un profil fédéré sans adresse e-mail");
        }
        if (displayName == null || displayName.isBlank()) {
            // Le fournisseur peut ne rien savoir du nom : l'adresse fait un nom d'attente
            // acceptable, que l'écran de profil propose de changer.
            displayName = email.split("@")[0];
        }
    }
}
