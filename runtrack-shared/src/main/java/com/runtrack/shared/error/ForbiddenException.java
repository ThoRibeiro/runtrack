package com.runtrack.shared.error;

/** L'appelant est identifié, mais n'a pas le droit de faire cela. */
public final class ForbiddenException extends DomainException {

    public ForbiddenException(String code, String message) {
        super(code, message);
    }
}
