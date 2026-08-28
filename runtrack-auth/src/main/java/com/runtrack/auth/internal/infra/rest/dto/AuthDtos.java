package com.runtrack.auth.internal.infra.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Les contrats HTTP d'{@code auth}. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record SignUpRequest(
            @NotBlank @Size(min = 3, max = 30) String handle,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 80) String displayName,
            @NotBlank @Size(min = 12, max = 200) String password) {
    }

    public record LogInRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 12, max = 200) String password) {
    }

    /** {@code tokenType} explicite : le client sait quoi mettre dans l'en-tête Authorization. */
    public record SessionResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn) {

        public static SessionResponse of(String accessToken, String refreshToken, long expiresIn) {
            return new SessionResponse(accessToken, refreshToken, "Bearer", expiresIn);
        }
    }

    public record SignUpResponse(String userId) {
    }
}
