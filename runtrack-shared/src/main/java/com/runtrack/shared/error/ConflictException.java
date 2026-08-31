package com.runtrack.shared.error;

/** L'état actuel de la ressource interdit l'opération demandée. */
public final class ConflictException extends DomainException {

    public ConflictException(String code, String message) {
        super(code, message);
    }

    /** Conserve la cause : un conflit sans trace d'origine est indiagnosticable en production. */
    public ConflictException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
