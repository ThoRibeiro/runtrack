package com.runtrack.sharing.usecases.model.link;

import java.time.Clock;
import java.util.UUID;
import java.util.random.RandomGenerator;

/** Identifiant d'un lien de partage — celui qui apparaît dans l'URL de révocation. */
public record ShareLinkId(UUID value) {

    public ShareLinkId {
        if (value == null) {
            throw new IllegalArgumentException("ShareLinkId requiert une valeur");
        }
    }

    public static ShareLinkId generate(Clock clock, RandomGenerator random) {
        return new ShareLinkId(com.runtrack.shared.id.UuidV7.from(clock.instant(), random));
    }

    public static ShareLinkId of(String value) {
        try {
            return new ShareLinkId(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("ShareLinkId invalide : " + value, e);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
