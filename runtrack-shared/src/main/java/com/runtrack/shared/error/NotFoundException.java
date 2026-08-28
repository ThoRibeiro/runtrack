package com.runtrack.shared.error;

/** La ressource demandée n'existe pas, ou n'existe plus. */
public final class NotFoundException extends DomainException {

    public NotFoundException(String code, String message) {
        super(code, message);
    }
}
