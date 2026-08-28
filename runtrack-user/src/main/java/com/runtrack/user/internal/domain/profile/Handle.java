package com.runtrack.user.internal.domain.profile;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * L'identifiant public choisi par l'utilisateur, celui qui apparaît dans les URL.
 *
 * <p>Normalisé en minuscules : sans cela, {@code Marie} et {@code marie} seraient deux
 * comptes distincts et indiscernables à l'œil, ce qui est une porte ouverte à l'usurpation.
 */
public record Handle(String value) {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 30;

    private static final Pattern ALLOWED = Pattern.compile("^[a-z0-9](?:[a-z0-9_.-]*[a-z0-9])?$");

    public Handle {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Identifiant public absent");
        }
        value = value.strip().toLowerCase(Locale.ROOT);
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "L'identifiant public fait entre " + MIN_LENGTH + " et " + MAX_LENGTH
                            + " caractères, reçu " + value.length());
        }
        if (!ALLOWED.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "L'identifiant public n'accepte que lettres, chiffres, point, tiret et souligné, "
                            + "et doit commencer et finir par une lettre ou un chiffre");
        }
    }
}
