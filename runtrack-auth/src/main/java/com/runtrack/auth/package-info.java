/**
 * Inscription, connexion, JWT, refresh rotatif, vérification d'email. Appelle
 * {@code UserApi} pour créer le profil ; si {@code auth} publiait {@code UserRegistered}
 * et que {@code user} l'écoutait, les deux modules formeraient un cycle.
 */
@org.springframework.modulith.ApplicationModule(displayName = "auth",
        allowedDependencies = {"user"})
package com.runtrack.auth;
