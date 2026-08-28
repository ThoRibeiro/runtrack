package com.runtrack.auth.internal.infra.security;

import com.runtrack.shared.access.Viewer;
import com.runtrack.shared.id.UserId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Traduit un jeton d'accès en {@link Viewer} et le pose dans le contexte de sécurité.
 *
 * <p>C'est ce filtre qui permet aux autres modules d'obtenir le lecteur courant sans jamais
 * dépendre d'{@code auth} : ils reçoivent un {@code Viewer}, type du noyau partagé.
 *
 * <p>Un jeton invalide ne provoque pas d'erreur ici : la requête continue en anonyme, et
 * c'est la règle d'autorisation qui tranche. Une ressource publique reste accessible avec un
 * jeton expiré, ce qui évite de déconnecter un lecteur pour une page qui ne demandait rien.
 */
@Component
class ViewerAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder decoder;

    ViewerAuthenticationFilter(JwtDecoder decoder) {
        this.decoder = decoder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        bearerTokenOf(request).ifPresent(token -> {
            try {
                Jwt jwt = decoder.decode(token);
                Viewer viewer = new Viewer.AuthenticatedUser(UserId.of(jwt.getSubject()));
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(viewer, null, List.of()));
            } catch (JwtException | IllegalArgumentException expired) {
                SecurityContextHolder.clearContext();
            }
        });
        chain.doFilter(request, response);
    }

    private static java.util.Optional<String> bearerTokenOf(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        return header != null && header.startsWith(BEARER_PREFIX)
                ? java.util.Optional.of(header.substring(BEARER_PREFIX.length()))
                : java.util.Optional.empty();
    }
}
