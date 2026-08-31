package com.runtrack.auth.infrastructure.endpoint;

import com.runtrack.auth.usecases.service.AuthenticatedSession;
import com.runtrack.auth.usecases.service.Authentication;
import com.runtrack.auth.usecases.model.credential.Password;
import com.runtrack.auth.infrastructure.dto.AuthDtos;
import com.runtrack.platform.ratelimit.RateLimitProperties;
import com.runtrack.platform.ratelimit.RateLimiter;
import com.runtrack.platform.openapi.ApiFolders;
import com.runtrack.shared.error.TooManyRequestsException;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Les points d'entrée d'authentification, tous publics : ce sont eux qui délivrent la
 * preuve que les autres exigent.
 *
 * <p><b>La connexion est bridée sur deux axes</b> (§9), et il en faut deux. Par adresse IP, contre
 * le script qui essaie mille comptes depuis une machine ; par compte, contre celui qui essaie
 * mille mots de passe sur une adresse connue en tournant les IP. Chacun pris seul laisse passer
 * l'autre attaque.
 *
 * <p>Le compteur par compte est armé <b>avant</b> la vérification du mot de passe, et donc aussi
 * par les tentatives qui échouent : ne compter que les succès reviendrait à ne rien compter.
 */
@RestController
@ApiFolders.Authentication
@RequestMapping("/auth/v1")
class AuthController {

    private final Authentication authentication;
    private final RateLimiter rateLimiter;
    private final RateLimitProperties quotas;

    AuthController(Authentication authentication, RateLimiter rateLimiter,
            RateLimitProperties quotas) {

        this.authentication = authentication;
        this.rateLimiter = rateLimiter;
        this.quotas = quotas;
    }

    @Operation(summary = "Créer un compte")
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    AuthDtos.SignUpResponse signUp(@Valid @RequestBody AuthDtos.SignUpRequest request) {
        var id = authentication.signUp(
                request.handle(), request.email(), request.displayName(), new Password(request.password()));
        return new AuthDtos.SignUpResponse(id.toString());
    }

    @Operation(summary = "Ouvrir une session")
    @PostMapping("/login")
    AuthDtos.SessionResponse logIn(HttpServletRequest http,
            @Valid @RequestBody AuthDtos.LogInRequest request) {

        requireQuota("login:ip:" + clientAddressOf(http), quotas.loginPerIp());
        requireQuota("login:account:" + request.email().toLowerCase(java.util.Locale.ROOT),
                quotas.loginPerAccount());
        return toResponse(authentication.logIn(request.email(), new Password(request.password())));
    }

    /**
     * Le même message quel que soit l'axe dépassé.
     *
     * <p>Distinguer « trop d'essais depuis cette adresse » de « trop d'essais sur ce compte »
     * dirait à un attaquant lequel des deux il a saturé — et confirmerait au passage que le compte
     * existe.
     */
    private void requireQuota(String key, int limit) {
        if (!rateLimiter.tryAcquire(key, limit, quotas.loginWindow())) {
            throw new TooManyRequestsException("TOO_MANY_ATTEMPTS",
                    "Trop de tentatives, réessayez dans quelques minutes");
        }
    }

    /**
     * L'adresse du client, en tenant compte du proxy.
     *
     * <p>{@code X-Forwarded-For} est lu parce que l'application tourne derrière un ingress, où
     * {@code getRemoteAddr()} rendrait l'adresse du proxy — c'est-à-dire la même pour tout le
     * monde, ce qui ferait du compteur par IP un compteur global. On ne garde que la première
     * valeur, la seule que l'ingress ajoute lui-même.
     */
    private static String clientAddressOf(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return http.getRemoteAddr();
        }
        int firstSeparator = forwarded.indexOf(',');
        return (firstSeparator < 0 ? forwarded : forwarded.substring(0, firstSeparator)).trim();
    }

    @Operation(summary = "Renouveler le jeton d'accès")
    @PostMapping("/refresh")
    AuthDtos.SessionResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest request) {
        return toResponse(authentication.refresh(request.refreshToken()));
    }

    @Operation(summary = "Fermer la session")
    @PostMapping("/logout")
    ResponseEntity<Void> logOut(@Valid @RequestBody AuthDtos.RefreshRequest request) {
        authentication.logOut(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Confirmer une adresse e-mail")
    @GetMapping("/verify-email")
    ResponseEntity<Void> verifyEmail(@RequestParam("token") String token) {
        authentication.confirmEmail(token);
        return ResponseEntity.noContent().build();
    }

    /**
     * Répond 202 quelle que soit l'issue : dire si l'adresse existe ferait de cet endpoint
     * un énumérateur de comptes.
     */
    @Operation(summary = "Demander la réinitialisation du mot de passe")
    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void forgotPassword(@Valid @RequestBody AuthDtos.ForgotPasswordRequest request) {
        authentication.requestPasswordReset(request.email());
    }

    @Operation(summary = "Choisir un nouveau mot de passe")
    @PostMapping("/password/reset")
    ResponseEntity<Void> resetPassword(@Valid @RequestBody AuthDtos.ResetPasswordRequest request) {
        authentication.resetPassword(request.token(), new Password(request.password()));
        return ResponseEntity.noContent().build();
    }

    private static AuthDtos.SessionResponse toResponse(AuthenticatedSession session) {
        return AuthDtos.SessionResponse.of(
                session.accessToken(), session.refreshToken(), session.expiresInSeconds());
    }
}
