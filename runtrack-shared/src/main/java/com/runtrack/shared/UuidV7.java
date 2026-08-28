package com.runtrack.shared;

import java.time.Instant;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Génération d'UUID version 7 (RFC 9562) : 48 bits d'horodatage en tête, puis de
 * l'aléatoire. Le JDK 25 ne sait produire que des v4.
 *
 * <p>Les identifiants sont des clés primaires, et une clé v4 se disperse dans l'index :
 * l'ordre temporel de la v7 garde les insertions groupées en fin de B-tree.
 */
public final class UuidV7 {

    private static final int VERSION = 7;
    private static final int VARIANT_RFC_9562 = 0b10;

    private UuidV7() {
    }

    public static UUID from(Instant timestamp, RandomGenerator random) {
        long millis = timestamp.toEpochMilli();
        if (millis < 0) {
            throw new IllegalArgumentException("UUID v7 ne peut pas encoder une date antérieure à l'epoch : " + timestamp);
        }

        long randomA = random.nextInt(1 << 12);
        long high = (millis << 16) | ((long) VERSION << 12) | randomA;

        long randomB = random.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL;
        long low = ((long) VARIANT_RFC_9562 << 62) | randomB;

        return new UUID(high, low);
    }
}
