package com.runtrack.shared.error;

/**
 * La base des erreurs métier. Chacune porte un {@code code} stable, distinct du statut
 * HTTP : le client s'y branche pour réagir, et il ne doit pas bouger quand on change un
 * message ou qu'on remplace un 409 par un 422.
 */
public abstract sealed class DomainException extends RuntimeException
        permits NotFoundException, ConflictException, ForbiddenException {

    private final String code;

    protected DomainException(String code, String message) {
        this(code, message, null);
    }

    protected DomainException(String code, String message, Throwable cause) {
        super(message, cause);
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Une erreur métier sans code n'est pas exploitable côté client");
        }
        this.code = code;
    }

    public String code() {
        return code;
    }
}
