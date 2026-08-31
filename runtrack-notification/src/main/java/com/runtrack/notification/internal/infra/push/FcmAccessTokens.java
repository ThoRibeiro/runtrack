package com.runtrack.notification.internal.infra.push;

/**
 * De quoi signer un appel à Firebase.
 *
 * <p>Une interface pour une seule implémentation, mais elle isole la seule partie de l'envoi qui
 * ne peut pas être exercée en test : obtenir un jeton auprès de Google demande un vrai compte de
 * service et un vrai aller-retour réseau. Derrière cette couture, tout le reste de
 * {@link FcmPushSender} — le découpage en lots, la forme du corps, la lecture des jetons invalides
 * — se teste contre un serveur simulé.
 */
interface FcmAccessTokens {

    String current();
}
