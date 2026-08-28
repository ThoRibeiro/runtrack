package com.runtrack.shared;

import java.time.Clock;
import java.util.UUID;
import java.util.random.RandomGenerator;

/** Identifiant d'un utilisateur. */
public record UserId(UUID value) {

    public UserId {
        if (value == null) {
            throw new IllegalArgumentException("UserId requiert une valeur");
        }
    }

    public static UserId generate(Clock clock, RandomGenerator random) {
        return new UserId(UuidV7.from(clock.instant(), random));
    }

    public static UserId of(String value) {
        try {
            return new UserId(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("UserId invalide : " + value, e);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
