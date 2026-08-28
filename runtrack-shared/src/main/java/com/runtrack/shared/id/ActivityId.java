package com.runtrack.shared.id;

import java.time.Clock;
import java.util.UUID;
import java.util.random.RandomGenerator;

/** Identifiant d'une course. */
public record ActivityId(UUID value) {

    public ActivityId {
        if (value == null) {
            throw new IllegalArgumentException("ActivityId requiert une valeur");
        }
    }

    public static ActivityId generate(Clock clock, RandomGenerator random) {
        return new ActivityId(UuidV7.from(clock.instant(), random));
    }

    public static ActivityId of(String value) {
        try {
            return new ActivityId(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("ActivityId invalide : " + value, e);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
