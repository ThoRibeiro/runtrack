package com.runtrack.user.infrastructure.endpoint;

import com.runtrack.platform.openapi.ApiFolders;
import com.runtrack.user.usecases.model.profile.StoredImage;
import com.runtrack.user.usecases.service.UserAccounts;
import io.swagger.v3.oas.annotations.Operation;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Les photos de profil, rendues telles quelles.
 *
 * <p>Sur son propre préfixe, et **sans authentification** : une balise image d'application
 * mobile ne porte pas d'en-tête {@code Authorization}, et un avatar accompagne déjà un
 * profil public. Ce n'est pas une porte dérobée — l'identifiant est un UUID aléatoire, il
 * ne se devine pas et ne dit rien de son propriétaire.
 *
 * <p>Immuable par construction : une nouvelle photo reçoit un nouvel identifiant, jamais
 * le même contenu sous la même adresse. D'où le cache d'un an, qui évite de retélécharger
 * la même vignette à chaque affichage d'une liste.
 */
@RestController
@ApiFolders.Accounts
@RequestMapping("/media/v1")
class AvatarController {

    private static final Duration CACHE_LIFETIME = Duration.ofDays(365);

    private final UserAccounts accounts;

    AvatarController(UserAccounts accounts) {
        this.accounts = accounts;
    }

    @Operation(summary = "Lire une photo de profil")
    @GetMapping("/avatars/{id}")
    ResponseEntity<byte[]> avatar(@PathVariable String id) {
        return accounts.avatarImage(id)
                .map(AvatarController::rendered)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static ResponseEntity<byte[]> rendered(StoredImage image) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(CACHE_LIFETIME).cachePublic().immutable())
                .body(image.bytes());
    }
}
