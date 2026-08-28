package com.runtrack.auth.internal.domain.credential;

/**
 * Un mot de passe en clair, le temps de le valider puis de le hacher.
 *
 * <p>La robustesse est vérifiée <em>ici</em>, pas dans le contrôleur : le jour où un
 * second point d'entrée définit un mot de passe — réinitialisation, import, console
 * d'administration — la règle le suit sans qu'on ait à y penser.
 *
 * <p>Longueur d'abord, composition ensuite. Imposer un caractère de chaque classe produit
 * surtout des mots de passe de douze signes qui finissent par {@code !1}, alors que la
 * longueur, elle, augmente réellement l'entropie.
 */
public record Password(String value) {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 200;

    public Password {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Mot de passe absent");
        }
        if (value.length() < MIN_LENGTH) {
            throw new IllegalArgumentException(
                    "Le mot de passe fait au moins " + MIN_LENGTH + " caractères");
        }
        if (value.length() > MAX_LENGTH) {
            // Une entrée démesurée est un vecteur de déni de service sur le hachage Argon2.
            throw new IllegalArgumentException(
                    "Le mot de passe dépasse " + MAX_LENGTH + " caractères");
        }
        if (value.chars().distinct().count() < 5) {
            throw new IllegalArgumentException(
                    "Le mot de passe est trop répétitif");
        }
    }
}
