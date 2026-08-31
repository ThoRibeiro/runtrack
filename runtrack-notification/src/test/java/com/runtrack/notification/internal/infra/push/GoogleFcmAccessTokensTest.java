package com.runtrack.notification.internal.infra.push;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * Ce que fait l'authentification Firebase quand elle est mal configurée.
 *
 * <p>C'est la seule partie de cette classe qu'un test puisse atteindre — obtenir un jeton demande
 * un vrai compte de service — et c'est aussi celle qui compte le plus : elle doit échouer au
 * <b>démarrage</b>. Une configuration incomplète qui n'explose qu'au premier push explose la nuit
 * où le premier coureur part.
 */
class GoogleFcmAccessTokensTest {

    private static PushProperties withCredentials(String location) {
        return new PushProperties("runtrack", location, null, null, Duration.ofSeconds(1));
    }

    @Test
    void refusesToStartWithoutAServiceAccount() {
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> new GoogleFcmAccessTokens(
                        withCredentials(null), new DefaultResourceLoader()))
                .withMessageContaining("credentials-location");
    }

    @Test
    void refusesToStartWhenTheServiceAccountCannotBeRead() {
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> new GoogleFcmAccessTokens(
                        withCredentials("classpath:pas-de-compte-de-service.json"),
                        new DefaultResourceLoader()))
                .withMessageContaining("illisible");
    }
}
