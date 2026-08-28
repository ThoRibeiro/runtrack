package com.runtrack.auth.internal.application;

/** Ce qu'une connexion ou un rafraîchissement rend au client. */
public record AuthenticatedSession(String accessToken, String refreshToken, long expiresInSeconds) {
}
