package com.runtrack.auth.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class SmtpAuthMailerTest {

    private static final AuthMailProperties PROPERTIES =
            new AuthMailProperties("smtp", "no-reply@runtrack.app", "https://runtrack.app");

    @Test
    void sendsTheVerificationLinkToTheAddressThatSignedUp() throws Exception {
        var sender = new CapturingMailSender();
        new SmtpAuthMailer(sender, PROPERTIES).sendEmailVerification("thomas@exemple.fr", "abc");

        MimeMessage sent = sender.only();
        assertThat(sent.getAllRecipients()).hasSize(1);
        assertThat(sent.getAllRecipients()[0]).hasToString("thomas@exemple.fr");
        assertThat(sent.getFrom()[0]).hasToString("no-reply@runtrack.app");
        assertThat(sent.getSubject()).isEqualTo("Confirmez votre adresse");
    }

    /**
     * Le lien doit être dans <b>les deux</b> versions.
     *
     * <p>Un client qui n'affiche pas le HTML — mode texte, montre, messagerie d'entreprise qui le
     * neutralise — recevrait sinon un message sans le seul geste qu'il demande.
     */
    @Test
    void putsTheLinkInBothTheTextAndTheHtmlPart() throws Exception {
        var sender = new CapturingMailSender();
        new SmtpAuthMailer(sender, PROPERTIES).sendEmailVerification("thomas@exemple.fr", "abc");

        List<String> parts = partsOf(sender.only());
        assertThat(parts).hasSize(2);
        assertThat(parts).allSatisfy(part ->
                assertThat(part).contains("https://runtrack.app/verify-email?token=abc"));
        assertThat(parts.getLast())
                .as("la version riche porte les couleurs de l'application")
                .contains("#2563EB")
                .contains("RunTrack");
    }

    @Test
    void sendsTheResetLinkOnItsOwnRoute() throws Exception {
        var sender = new CapturingMailSender();
        new SmtpAuthMailer(sender, PROPERTIES).sendPasswordReset("thomas@exemple.fr", "abc");

        assertThat(partsOf(sender.only())).allSatisfy(part ->
                assertThat(part).contains("https://runtrack.app/reset-password?token=abc"));
    }

    /**
     * Le compte est déjà créé et le jeton déjà enregistré quand l'envoi part. Laisser remonter
     * l'échec ferait échouer une inscription réussie, et la personne se retrouverait sans compte
     * <em>et</em> sans message.
     */
    @Test
    void aRelayThatRefusesDoesNotUndoTheSignUp() {
        var refusing = new CapturingMailSender() {
            @Override
            public void send(MimeMessage message) {
                throw new MailSendException("relais injoignable");
            }
        };

        assertThatCode(() -> new SmtpAuthMailer(refusing, PROPERTIES)
                .sendEmailVerification("thomas@exemple.fr", "abc"))
                .doesNotThrowAnyException();
    }

    /**
     * Les corps des deux parties, du repli le plus pauvre au plus riche.
     *
     * <p>La descente est récursive parce que {@code MimeMessageHelper} emboîte trois niveaux —
     * mixed, related, alternative — pour pouvoir accueillir un jour une pièce jointe ou une
     * image intégrée. Chercher les corps à une profondeur fixe lierait ce test à un détail
     * d'implémentation de Spring.
     */
    private static List<String> partsOf(MimeMessage message) throws Exception {
        var bodies = new ArrayList<String>();
        collect(message.getContent(), bodies);
        return bodies;
    }

    private static void collect(Object content, List<String> bodies) throws Exception {
        if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                collect(multipart.getBodyPart(i).getContent(), bodies);
            }
        } else {
            bodies.add(content.toString());
        }
    }

    /**
     * Un double écrit à la main plutôt qu'un mock : le projet n'utilise pas Mockito. Il délègue
     * la fabrication des messages à l'implémentation réelle — un {@code MimeMessage} construit à
     * la main ne vaudrait rien comme sujet de test — et retient ce qui part.
     */
    private static class CapturingMailSender extends JavaMailSenderImpl {

        private final List<MimeMessage> sent = new ArrayList<>();

        MimeMessage only() {
            assertThat(sent).hasSize(1);
            return sent.getFirst();
        }

        @Override
        public void send(MimeMessage message) {
            sent.add(message);
        }
    }
}
