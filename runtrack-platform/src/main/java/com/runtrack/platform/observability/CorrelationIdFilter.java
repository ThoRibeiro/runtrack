package com.runtrack.platform.observability;

import com.runtrack.shared.context.CallContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ouvre la portée de corrélation d'une requête HTTP, et la rend au client.
 *
 * <p>L'identifiant vient de l'appelant s'il en fournit un — c'est ce qui permet à une application
 * mobile de relier ce qu'elle voit à ce que le serveur a journalisé —, sinon il est tiré ici. Il
 * repart dans l'en-tête de réponse dans les deux cas : sans cela, un utilisateur qui signale un
 * problème n'a rien à citer.
 *
 * <p>Passe <b>avant</b> la chaîne de sécurité, et l'ordre est explicite. Placé derrière, le filtre
 * ne verrait jamais les requêtes que la sécurité rejette : un 401, un 403 ou un 429 repartirait
 * sans identifiant — c'est-à-dire précisément dans les cas où l'utilisateur a quelque chose à
 * signaler et où le support a besoin de quelque chose à chercher.
 *
 * <p>Conséquence assumée : le compte n'est pas encore résolu à cet instant, et le contexte est
 * donc anonyme. Ce n'est pas une perte — rien ne lit le compte du {@link CallContext}, et
 * l'identité figure déjà dans le contexte de sécurité que les journaux d'accès rapportent.
 */
@Component
@Order(CorrelationIdFilter.BEFORE_SECURITY)
class CorrelationIdFilter extends OncePerRequestFilter {

    /** Juste devant la chaîne de Spring Security, dont l'ordre par défaut est -100. */
    static final int BEFORE_SECURITY = -110;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        String correlationId = Optional.ofNullable(request.getHeader(CorrelationId.HEADER))
                .filter(value -> !value.isBlank())
                .orElseGet(CorrelationId::generate);

        response.setHeader(CorrelationId.HEADER, correlationId);
        MDC.put(CorrelationId.MDC_KEY, correlationId);
        try {
            // callWith et non runWith : la chaîne de filtres déclare des exceptions vérifiées,
            // et les envelopper ici ferait perdre le type que le gestionnaire d'erreurs attend.
            CallContext.anonymous(correlationId).<Void, Exception>callWith(() -> {
                chain.doFilter(request, response);
                return null;
            });
        } catch (IOException | ServletException | RuntimeException known) {
            throw known;
        } catch (Exception impossible) {
            throw new IllegalStateException("La chaîne de filtres n'en lève pas d'autres", impossible);
        } finally {
            // Dans un finally, et sans condition : un MDC qui fuit sur un fil réutilisé étiquette
            // les requêtes suivantes avec l'identifiant de la précédente.
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }
}
