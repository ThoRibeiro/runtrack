package com.runtrack.notification.usecases.model.push;

import static org.assertj.core.api.Assertions.assertThat;

import com.runtrack.notification.usecases.model.inbox.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PushComposerTest {

    private static final String LINK = "/activities/abc/live";

    @Test
    void aStartedRunSaysWhoAndInvitesToWatchLive() {
        PushMessage message = PushComposer.compose(
                NotificationType.FRIEND_STARTED_ACTIVITY, "Marie", LINK);

        assertThat(message.title()).isEqualTo("Marie vient de démarrer une course");
        assertThat(message.deepLink()).isEqualTo(LINK);
    }

    /**
     * Un compte supprimé entre l'événement et l'envoi ne doit pas empêcher le push : il le rend
     * impersonnel, ce qui est très différent de le perdre.
     */
    @Test
    void anUnknownActorMakesTheMessageImpersonalNotAbsent() {
        assertThat(PushComposer.compose(NotificationType.NEW_FOLLOWER, null, LINK).body())
                .isEqualTo("Quelqu'un suit désormais vos courses");
        assertThat(PushComposer.compose(NotificationType.NEW_FOLLOWER, "  ", LINK).body())
                .isEqualTo("Quelqu'un suit désormais vos courses");
    }

    /**
     * Toute nature doit produire un message.
     *
     * <p>Le {@code switch} est exhaustif, donc le compilateur attrape déjà l'oubli — mais pas le
     * libellé vide, qui ferait afficher une notification muette sur l'écran verrouillé.
     */
    @ParameterizedTest
    @EnumSource(NotificationType.class)
    void everyNatureProducesSomethingReadable(NotificationType type) {
        PushMessage message = PushComposer.compose(type, "Marie", LINK);

        assertThat(message.title()).isNotBlank();
        assertThat(message.body()).isNotBlank();
        assertThat(message.deepLink()).isEqualTo(LINK);
    }
}
