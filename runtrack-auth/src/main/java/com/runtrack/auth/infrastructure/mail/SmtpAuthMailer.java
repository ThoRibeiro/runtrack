package com.runtrack.auth.infrastructure.mail;

import com.runtrack.auth.usecases.port.AuthMailer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * L'envoi réel, par SMTP. En développement le serveur est Mailpit, en production le relais
 * désigné par {@code spring.mail.*}.
 *
 * <p>Chaque message part en <b>deux versions</b> — texte et HTML — dans le même envoi. Le client
 * affiche celle qu'il sait rendre : un lecteur en mode texte, une montre, un client d'entreprise
 * qui neutralise le HTML reçoivent alors le lien en clair plutôt qu'un message vide.
 *
 * <p><b>Un échec d'envoi n'annule pas l'inscription.</b> Le compte est créé et le jeton est
 * enregistré : faire remonter l'exception ferait échouer un {@code signUp} qui a réussi, et la
 * personne se retrouverait sans compte <em>et</em> sans message. L'échec est journalisé, et le
 * parcours « renvoyer le lien » est la façon de s'en sortir.
 */
@Component
@ConditionalOnProperty(name = "runtrack.mail.provider", havingValue = "smtp")
class SmtpAuthMailer implements AuthMailer {

    private static final Logger LOG = LoggerFactory.getLogger(SmtpAuthMailer.class);

    private final JavaMailSender sender;
    private final AuthMailProperties properties;

    SmtpAuthMailer(JavaMailSender sender, AuthMailProperties properties) {
        this.sender = sender;
        this.properties = properties;
    }

    @Override
    public void sendEmailVerification(String emailAddress, String secret) {
        String link = properties.emailVerificationLink(secret);
        send(emailAddress, "Confirmez votre adresse",
                AuthMailTemplates.verificationText(link),
                AuthMailTemplates.verificationHtml(link));
    }

    @Override
    public void sendPasswordReset(String emailAddress, String secret) {
        String link = properties.passwordResetLink(secret);
        send(emailAddress, "Réinitialisez votre mot de passe",
                AuthMailTemplates.passwordResetText(link),
                AuthMailTemplates.passwordResetHtml(link));
    }

    private void send(String to, String subject, String text, String html) {
        try {
            MimeMessage message = sender.createMimeMessage();
            // `true` : multipart alternatif. Le texte est ajouté en premier, le HTML ensuite —
            // c'est l'ordre que la RFC 2046 impose, du repli le plus pauvre au plus riche.
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(properties.from());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, html);
            sender.send(message);
        } catch (MessagingException | org.springframework.mail.MailException failure) {
            // L'adresse n'est pas journalisée : ces journaux sont conservés, une adresse est une
            // donnée personnelle, et le sujet suffit à savoir quel envoi a échoué.
            LOG.error("Envoi du courriel « {} » impossible", subject, failure);
        }
    }
}
