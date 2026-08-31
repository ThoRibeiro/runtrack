package com.runtrack.notification.internal.infra.push;

import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Le jeton d'accès Firebase, tiré du compte de service.
 *
 * <p>Le fichier est désigné par un chemin de configuration et jamais recopié dans le dépôt (§9).
 * {@link GoogleCredentials} garde le jeton en mémoire et ne le renouvelle qu'à l'approche de son
 * expiration : appeler {@link #current()} à chaque push ne déclenche donc pas un aller-retour à
 * chaque push.
 */
@Component
@ConditionalOnProperty(name = "runtrack.push.provider", havingValue = "fcm")
class GoogleFcmAccessTokens implements FcmAccessTokens {

    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

    private final GoogleCredentials credentials;

    GoogleFcmAccessTokens(PushProperties properties, ResourceLoader loader) {
        if (properties.credentialsLocation() == null || properties.credentialsLocation().isBlank()) {
            throw new IllegalStateException(
                    "runtrack.push.provider=fcm exige runtrack.push.credentials-location");
        }
        try (InputStream serviceAccount =
                loader.getResource(properties.credentialsLocation()).getInputStream()) {
            this.credentials = GoogleCredentials.fromStream(serviceAccount)
                    .createScoped(List.of(SCOPE));
        } catch (IOException unreadable) {
            // Échouer au démarrage, et non au premier push : une configuration incomplète doit se
            // voir tout de suite, pas la nuit où le premier coureur part.
            throw new IllegalStateException(
                    "Compte de service Firebase illisible : " + properties.credentialsLocation(),
                    unreadable);
        }
    }

    @Override
    public String current() {
        try {
            credentials.refreshIfExpired();
            return credentials.getAccessToken().getTokenValue();
        } catch (IOException unreachable) {
            throw new IllegalStateException("Jeton Firebase indisponible", unreachable);
        }
    }
}
