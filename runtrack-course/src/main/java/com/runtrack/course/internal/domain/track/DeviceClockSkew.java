package com.runtrack.course.internal.domain.track;

import com.runtrack.shared.error.ConflictException;
import java.time.Duration;
import java.time.Instant;

/**
 * L'écart entre l'horloge du téléphone et celle du serveur, mesuré une fois au démarrage
 * puis appliqué à tous les horodatages de la course.
 *
 * <p>Le téléphone est la seule source du temps de capture, et il dérive. Sans correction,
 * un décalage de trois minutes fait rejeter tous les points comme « dans le futur », ou
 * pire, produit une durée en mouvement fausse sans que rien ne le signale.
 */
public record DeviceClockSkew(Duration offset) {

    /** Au-delà, on ne corrige plus : on refuse de démarrer plutôt que d'inventer des statistiques. */
    public static final Duration MAX_ACCEPTABLE = Duration.ofMinutes(15);

    public static final DeviceClockSkew NONE = new DeviceClockSkew(Duration.ZERO);

    public DeviceClockSkew {
        if (offset == null) {
            throw new IllegalArgumentException("DeviceClockSkew sans écart");
        }
    }

    public static DeviceClockSkew observe(Instant deviceTime, Instant serverTime) {
        Duration offset = Duration.between(deviceTime, serverTime);
        if (offset.abs().compareTo(MAX_ACCEPTABLE) > 0) {
            throw new ConflictException(
                    "DEVICE_CLOCK_TOO_FAR_OFF",
                    "L'horloge du téléphone dérive de " + offset.abs().toMinutes()
                            + " min, au-delà des " + MAX_ACCEPTABLE.toMinutes() + " min tolérées");
        }
        return new DeviceClockSkew(offset);
    }

    public Instant correct(Instant deviceTime) {
        return deviceTime.plus(offset);
    }
}
