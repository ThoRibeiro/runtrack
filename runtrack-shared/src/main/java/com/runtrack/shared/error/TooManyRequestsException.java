package com.runtrack.shared.error;

/**
 * Le quota du §9 est dépassé.
 *
 * <p>Dans {@code shared} avec les autres erreurs métier, et non dans {@code platform} auprès du
 * limiteur : {@link DomainException} est scellée, et un type scellé ne se prolonge que depuis son
 * propre paquet. C'est aussi le bon endroit — le vocabulaire d'erreur est un contrat partagé, pas
 * un détail d'implémentation du limiteur.
 */
public final class TooManyRequestsException extends DomainException {

    public TooManyRequestsException(String code, String message) {
        super(code, message);
    }
}
