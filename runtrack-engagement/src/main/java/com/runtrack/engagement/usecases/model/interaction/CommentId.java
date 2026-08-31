package com.runtrack.engagement.usecases.model.interaction;

import com.runtrack.shared.id.UuidV7;
import java.time.Clock;
import java.util.UUID;
import java.util.random.RandomGenerator;

/** Identifiant d'un commentaire. */
public record CommentId(UUID value) {

    public CommentId {
        if (value == null) {
            throw new IllegalArgumentException("CommentId requiert une valeur");
        }
    }

    public static CommentId generate(Clock clock, RandomGenerator random) {
        return new CommentId(UuidV7.from(clock.instant(), random));
    }

    public static CommentId of(String value) {
        try {
            return new CommentId(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("CommentId invalide : " + value, e);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
