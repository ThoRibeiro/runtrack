package com.runtrack.notification.internal.application;

import com.runtrack.notification.internal.application.port.DeviceTokenRepository;
import com.runtrack.notification.internal.domain.push.DevicePlatform;
import com.runtrack.notification.internal.domain.push.DeviceToken;
import com.runtrack.shared.error.NotFoundException;
import com.runtrack.shared.id.UserId;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Les appareils qu'un compte déclare, et ceux qu'il retire. */
@Service
public class DeviceRegistry {

    private final DeviceTokenRepository devices;
    private final Clock clock;

    public DeviceRegistry(DeviceTokenRepository devices, Clock clock) {
        this.devices = devices;
        this.clock = clock;
    }

    /** Réenregistrer le même appareil est sans effet visible : le client le fait à chaque lancement. */
    @Transactional
    public DeviceToken register(UserId ownerId, String token, DevicePlatform platform) {
        var device = new DeviceToken(token, ownerId, platform, clock.instant());
        devices.register(device);
        return device;
    }

    @Transactional
    public void forget(UserId ownerId, String token) {
        if (!devices.forget(ownerId, token)) {
            throw new NotFoundException("DEVICE_NOT_FOUND", "Appareil introuvable");
        }
    }

    @Transactional(readOnly = true)
    public List<DeviceToken> of(UserId ownerId) {
        return devices.of(ownerId);
    }
}
