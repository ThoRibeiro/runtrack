/**
 * Profil, préférences de compte et suppression RGPD. Ne dépend d'aucun autre module :
 * c'est la brique de base sur laquelle tout le reste s'appuie, et c'est lui — non
 * {@code auth} — qui publie {@code UserRegistered}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "user")
package com.runtrack.user;
