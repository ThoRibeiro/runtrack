package com.runtrack.auth.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Répond 401 quand aucune identité n'a été présentée.
 *
 * <p>Sans point d'entrée explicite, Spring Security répond 403 dans ce cas, ce qui brouille
 * la seule distinction utile au client : 401 veut dire « authentifie-toi », 403 veut dire
 * « tu es identifié mais ce n'est pas pour toi ». Le premier justifie de rafraîchir le
 * jeton, le second jamais.
 *
 * <p>Le corps suit RFC 9457, comme toutes les autres erreurs de l'API : le client n'a pas à
 * traiter un format à part pour les erreurs d'authentification.
 */
@Component
class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String BODY = """
            {"type":"https://runtrack.app/problems/authentication-required",\
            "title":"Unauthorized","status":401,\
            "detail":"Cette ressource demande d'être connecté","code":"AUTHENTICATION_REQUIRED"}""";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.getWriter().write(BODY);
    }
}
