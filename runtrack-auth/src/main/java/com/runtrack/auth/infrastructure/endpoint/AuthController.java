package com.runtrack.auth.infrastructure.endpoint;

import com.runtrack.auth.usecases.service.AuthenticatedSession;
import com.runtrack.auth.usecases.service.Authentication;
import com.runtrack.auth.usecases.model.credential.Password;
import com.runtrack.auth.infrastructure.dto.AuthDtos;
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
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final Authentication authentication;

    AuthController(Authentication authentication) {
        this.authentication = authentication;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    AuthDtos.SignUpResponse signUp(@Valid @RequestBody AuthDtos.SignUpRequest request) {
        var id = authentication.signUp(
                request.handle(), request.email(), request.displayName(), new Password(request.password()));
        return new AuthDtos.SignUpResponse(id.toString());
    }

    @PostMapping("/login")
    AuthDtos.SessionResponse logIn(@Valid @RequestBody AuthDtos.LogInRequest request) {
        return toResponse(authentication.logIn(request.email(), new Password(request.password())));
    }

    @PostMapping("/refresh")
    AuthDtos.SessionResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest request) {
        return toResponse(authentication.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logOut(@Valid @RequestBody AuthDtos.RefreshRequest request) {
        authentication.logOut(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/verify-email")
    ResponseEntity<Void> verifyEmail(@RequestParam("token") String token) {
        authentication.confirmEmail(token);
        return ResponseEntity.noContent().build();
    }

    /**
     * Répond 202 quelle que soit l'issue : dire si l'adresse existe ferait de cet endpoint
     * un énumérateur de comptes.
     */
    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void forgotPassword(@Valid @RequestBody AuthDtos.ForgotPasswordRequest request) {
        authentication.requestPasswordReset(request.email());
    }

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
