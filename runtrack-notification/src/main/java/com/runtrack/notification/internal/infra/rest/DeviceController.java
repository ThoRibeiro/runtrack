package com.runtrack.notification.internal.infra.rest;

import static com.runtrack.notification.internal.infra.rest.NotificationController.requireUser;

import com.runtrack.notification.internal.application.DeviceRegistry;
import com.runtrack.notification.internal.domain.push.DevicePlatform;
import com.runtrack.notification.internal.domain.push.DeviceToken;
import com.runtrack.notification.internal.infra.rest.dto.NotificationDtos;
import com.runtrack.shared.access.Viewer;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Les appareils sur lesquels un compte accepte des push.
 *
 * <p>Le jeton sert d'identifiant dans l'URL de suppression : c'est le seul que le client possède,
 * et lui en imposer un second l'obligerait à le mémoriser entre deux lancements.
 */
@RestController
@RequestMapping("/api/v1/users/me/devices")
class DeviceController {

    private final DeviceRegistry devices;

    DeviceController(DeviceRegistry devices) {
        this.devices = devices;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    NotificationDtos.DeviceResponse register(
            @AuthenticationPrincipal Viewer viewer,
            @Valid @RequestBody NotificationDtos.RegisterDeviceRequest request) {

        return toResponse(devices.register(
                requireUser(viewer), request.token(), DevicePlatform.valueOf(request.platform())));
    }

    @GetMapping
    NotificationDtos.DeviceListResponse list(@AuthenticationPrincipal Viewer viewer) {
        return new NotificationDtos.DeviceListResponse(
                devices.of(requireUser(viewer)).stream().map(DeviceController::toResponse).toList());
    }

    @DeleteMapping("/{token}")
    ResponseEntity<Void> forget(@AuthenticationPrincipal Viewer viewer, @PathVariable String token) {
        devices.forget(requireUser(viewer), token);
        return ResponseEntity.noContent().build();
    }

    private static NotificationDtos.DeviceResponse toResponse(DeviceToken device) {
        return new NotificationDtos.DeviceResponse(
                device.token(), device.platform().name(), device.registeredAt());
    }
}
