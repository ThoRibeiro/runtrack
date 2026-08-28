package com.runtrack.user.internal.domain.profile;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Une adresse e-mail, validée et normalisée à la construction.
 *
 * <p>La validation est délibérément permissive : la seule preuve qu'une adresse existe est
 * qu'un message y arrive, ce que fait la vérification d'e-mail. Un motif trop strict
 * rejette des adresses valides — apostrophes, domaines longs, sous-adressage — pour un
 * gain nul.
 */
public record Email(String value) {

    private static final Pattern SHAPE = Pattern.compile("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$");
    private static final int MAX_LENGTH = 254;

    public Email {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Adresse e-mail absente");
        }
        value = value.strip().toLowerCase(Locale.ROOT);
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Adresse e-mail trop longue : " + value.length() + " caractères");
        }
        if (!SHAPE.matcher(value).matches()) {
            throw new IllegalArgumentException("Adresse e-mail mal formée");
        }
    }

    /** Le domaine, utile aux journaux et aux statistiques sans exposer l'adresse entière. */
    public String domain() {
        return value.substring(value.indexOf('@') + 1);
    }
}
