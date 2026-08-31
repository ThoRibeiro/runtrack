package com.runtrack.sharing.infrastructure.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Le filtre de partage s'enregistre lui-même, <b>après</b> la chaîne de Spring Security.
 *
 * <p><b>S'enregistrer soi-même</b> évite le cycle : l'ajouter à la {@code SecurityFilterChain}
 * obligerait {@code auth} à connaître {@code sharing}, alors que le §5.4 est justement construit
 * pour que cette arête n'existe pas.
 *
 * <p><b>Après, et non avant</b>, et ce détail est tout sauf cosmétique. Placé devant, le filtre
 * pose bien son {@code Viewer.ShareLinkHolder} — puis la chaîne de sécurité s'exécute et installe
 * <em>son</em> contexte par-dessus. Le lecteur disparaissait, la course privée redevenait
 * introuvable, et rien ne signalait l'écrasement. Placé derrière, la chaîne a déjà laissé passer
 * {@code /api/v1/shared/**} (permitAll) et le contexte qu'on pose est le dernier mot.
 *
 * <p>Le réacheminement vers {@code course} ne repasse pas par la chaîne — celle-ci ne s'applique
 * qu'aux requêtes entrantes, asynchrones et d'erreur — de sorte que le lecteur arrive intact au
 * contrôleur. L'autorisation n'est pas contournée pour autant : {@code ActivityAccessPolicy}
 * tranche, comme pour toute autre lecture (§5.5).
 */
@Configuration
class ShareLinkFilterRegistration {

    @Bean
    FilterRegistrationBean<ShareLinkAccessFilter> shareLinkFilterChainEntry(
            ShareLinkAccessFilter filter) {

        var registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/v1/shared/*");
        // Juste après l'ordre par défaut de la chaîne de Spring Security (-100). La constante vit
        // dans une auto-configuration que ce module n'a pas à embarquer pour un entier.
        registration.setOrder(-99);
        return registration;
    }
}
