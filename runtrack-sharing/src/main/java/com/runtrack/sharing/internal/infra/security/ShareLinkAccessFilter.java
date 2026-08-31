package com.runtrack.sharing.internal.infra.security;

import com.runtrack.shared.access.Viewer;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.sharing.internal.application.ShareLinks;
import com.runtrack.sharing.internal.domain.link.ShareToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * La chaîne du §5.4, en une classe.
 *
 * <p>Une requête sur {@code /api/v1/shared/{token}/…} est résolue ici : le jeton devient un
 * {@link Viewer.ShareLinkHolder} déjà porteur de l'{@code ActivityId}, puis la requête est
 * <b>réacheminée</b> vers le chemin de {@code course} correspondant.
 *
 * <p>Le réacheminement est ce qui évite de réécrire les contrôleurs de {@code course} dans
 * {@code sharing} — y compris le flux SSE, que ce module n'a aucun moyen d'atteindre puisque la
 * mécanique du direct est {@code internal} chez son voisin. {@code course} reçoit un {@code Viewer}
 * et n'a jamais entendu parler de {@code sharing} ; le graphe reste acyclique.
 *
 * <p>Un réacheminement n'est pas refiltré par Spring Security — la chaîne ne s'applique qu'aux
 * requêtes entrantes — de sorte que le lecteur posé ici parvient intact au contrôleur.
 */
@Component
public class ShareLinkAccessFilter extends OncePerRequestFilter {

    static final String SHARED_PREFIX = "/api/v1/shared/";
    static final String ACTIVITIES_PREFIX = "/api/v1/activities/";

    private final ShareLinks links;

    ShareLinkAccessFilter(ShareLinks links) {
        this.links = links;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(SHARED_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        Optional<ActivityId> activity = tokenOf(request.getRequestURI())
                .flatMap(token -> links.resolve(new ShareToken(token)));

        if (activity.isEmpty()) {
            // Inconnu, révoqué ou expiré : la même réponse pour les trois. Distinguer confirmerait
            // à qui tâtonne qu'un autre jeton, lui, a existé.
            respondNotFound(response);
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new Viewer.ShareLinkHolder(activity.get()), null, List.of()));

        request.getRequestDispatcher(
                        ACTIVITIES_PREFIX + activity.get() + suffixOf(request.getRequestURI()))
                .forward(request, response);
    }

    /**
     * Écrit le refus directement, plutôt que par {@code sendError}.
     *
     * <p>{@code sendError} déclenche un aiguillage vers {@code /error}, que la chaîne de sécurité
     * filtre — et qui répondrait donc 401 à un jeton simplement inconnu. Écrire la réponse ici rend
     * le vrai statut, et dans le format du §8 par-dessus le marché.
     */
    private static void respondNotFound(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.NOT_FOUND.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"https://runtrack.app/problems/share-link-not-found","title":"Not Found",\
                "status":404,"detail":"Lien de partage introuvable","code":"SHARE_LINK_NOT_FOUND"}\
                """);
    }

    private static Optional<String> tokenOf(String uri) {
        String rest = uri.substring(SHARED_PREFIX.length());
        int slash = rest.indexOf('/');
        String token = slash < 0 ? rest : rest.substring(0, slash);
        return token.isBlank() ? Optional.empty() : Optional.of(token);
    }

    /** Ce qui suit le jeton — {@code /stream}, {@code /track} — voyage tel quel vers {@code course}. */
    private static String suffixOf(String uri) {
        String rest = uri.substring(SHARED_PREFIX.length());
        int slash = rest.indexOf('/');
        return slash < 0 ? "" : rest.substring(slash);
    }
}
