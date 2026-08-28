package com.runtrack.user;

import com.runtrack.shared.id.UserId;
import java.util.Optional;

/**
 * Ce qu'un autre module a le droit de savoir d'un profil : de quoi afficher un auteur.
 *
 * <p>Ni adresse e-mail, ni physiologie, ni état du compte. Un contrat inter-modules trop
 * généreux devient impossible à resserrer.
 */
public record UserSummary(UserId id, String handle, String displayName, Optional<String> avatarUrl) {

    public UserSummary {
        if (id == null || handle == null || displayName == null || avatarUrl == null) {
            throw new IllegalArgumentException("UserSummary incomplet");
        }
    }
}
