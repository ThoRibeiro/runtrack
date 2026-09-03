package com.runtrack.user.usecases.model.profile;

/**
 * Une image de profil telle qu'elle est conservée : son identifiant, son type et ses octets.
 *
 * <p>Le domaine ne connaît ni {@code MultipartFile} ni {@code HttpServletResponse} : il
 * manipule des octets et un type de contenu, et c'est la couche REST qui sait les recevoir
 * et les rendre.
 */
public record StoredImage(String id, String contentType, byte[] bytes) {

    public StoredImage {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Une image stockée a besoin d'un identifiant");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("Une image stockée a besoin d'un type de contenu");
        }
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Une image stockée a besoin d'octets");
        }
    }
}
