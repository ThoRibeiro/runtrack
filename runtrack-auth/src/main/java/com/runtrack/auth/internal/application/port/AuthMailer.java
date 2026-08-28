package com.runtrack.auth.internal.application.port;

/**
 * L'envoi des courriels d'authentification. Un port, donc le profil {@code local} peut
 * journaliser le lien au lieu d'exiger un serveur de messagerie pour développer.
 */
public interface AuthMailer {

    void sendEmailVerification(String emailAddress, String secret);

    void sendPasswordReset(String emailAddress, String secret);
}
