package com.runtrack.notification.internal.infra.rest;

import static com.runtrack.notification.internal.infra.rest.NotificationController.requireUser;

import com.runtrack.notification.internal.application.NotificationSettings;
import com.runtrack.notification.internal.domain.inbox.NotificationPreferences;
import com.runtrack.notification.internal.domain.inbox.NotificationType;
import com.runtrack.notification.internal.infra.rest.dto.NotificationDtos;
import com.runtrack.shared.access.Viewer;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Ce que le destinataire choisit de recevoir. */
@RestController
@RequestMapping("/api/v1/users/me/notification-preferences")
class NotificationPreferencesController {

    private final NotificationSettings settings;

    NotificationPreferencesController(NotificationSettings settings) {
        this.settings = settings;
    }

    @GetMapping
    NotificationDtos.PreferencesResponse read(@AuthenticationPrincipal Viewer viewer) {
        return toResponse(settings.of(requireUser(viewer)));
    }

    @PatchMapping
    NotificationDtos.PreferencesResponse update(
            @AuthenticationPrincipal Viewer viewer,
            @Valid @RequestBody NotificationDtos.PreferencesRequest request) {

        return toResponse(settings.mute(requireUser(viewer), parse(request.muted())));
    }

    /**
     * La réponse énumère aussi ce qui <em>existe</em>.
     *
     * <p>Sans cela, l'écran de réglages devrait tenir sa propre liste des natures, qui divergerait
     * de celle du serveur à la première qu'on ajoute.
     */
    private static NotificationDtos.PreferencesResponse toResponse(NotificationPreferences preferences) {
        return new NotificationDtos.PreferencesResponse(
                preferences.muted().stream().map(NotificationType::name).collect(Collectors.toSet()),
                Arrays.stream(NotificationType.values()).map(NotificationType::name)
                        .collect(Collectors.toCollection(java.util.LinkedHashSet::new)));
    }

    /** Une nature inconnue est une donnée invalide : 422, et non une préférence ignorée en silence. */
    private static Set<NotificationType> parse(Set<String> names) {
        var types = EnumSet.noneOf(NotificationType.class);
        names.forEach(name -> {
            try {
                types.add(NotificationType.valueOf(name));
            } catch (IllegalArgumentException unknown) {
                throw new IllegalArgumentException("Nature de notification inconnue : " + name, unknown);
            }
        });
        return types;
    }
}
