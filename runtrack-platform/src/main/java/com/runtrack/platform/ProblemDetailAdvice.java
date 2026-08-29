package com.runtrack.platform;

import com.runtrack.shared.error.ConflictException;
import com.runtrack.shared.error.DomainException;
import com.runtrack.shared.error.ForbiddenException;
import com.runtrack.shared.error.NotFoundException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduit les erreurs en {@code application/problem+json} (RFC 9457).
 *
 * <p>Chaque réponse porte un {@code code} métier <em>en plus</em> du statut. C'est lui que
 * le client teste : le statut se regroupe — trois causes distinctes rendent 409 — et il
 * arrive qu'on le change. Un code stable survit à ces deux choses.
 *
 * <p>Un {@link IllegalArgumentException} vient d'un objet valeur qui a refusé une entrée :
 * c'est une donnée invalide, donc 422, jamais une erreur serveur.
 */
@RestControllerAdvice
public class ProblemDetailAdvice {

    private static final String BASE_TYPE = "https://runtrack.app/problems/";

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail onNotFound(NotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, exception);
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail onConflict(ConflictException exception) {
        return problem(HttpStatus.CONFLICT, exception);
    }

    @ExceptionHandler(ForbiddenException.class)
    ProblemDetail onForbidden(ForbiddenException exception) {
        return problem(HttpStatus.FORBIDDEN, exception);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail onInvalidValue(IllegalArgumentException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
        detail.setTitle("Donnée invalide");
        detail.setType(URI.create(BASE_TYPE + "invalid-value"));
        detail.setProperty("code", "INVALID_VALUE");
        return detail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail onFailedValidation(MethodArgumentNotValidException exception) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        detail.setTitle("Requête invalide");
        detail.setType(URI.create(BASE_TYPE + "invalid-request"));
        detail.setProperty("code", "INVALID_REQUEST");
        detail.setProperty("errors", exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " : " + error.getDefaultMessage())
                .toList());
        return detail;
    }

    private static ProblemDetail problem(HttpStatus status, DomainException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        detail.setTitle(status.getReasonPhrase());
        detail.setType(URI.create(BASE_TYPE + exception.code().toLowerCase(java.util.Locale.ROOT).replace('_', '-')));
        detail.setProperty("code", exception.code());
        return detail;
    }
}
