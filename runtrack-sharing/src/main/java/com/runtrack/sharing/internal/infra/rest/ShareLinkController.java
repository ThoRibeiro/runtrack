package com.runtrack.sharing.internal.infra.rest;

import com.runtrack.shared.access.Viewer;
import com.runtrack.shared.error.ForbiddenException;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import com.runtrack.sharing.internal.application.ShareLinks;
import com.runtrack.sharing.internal.domain.link.ShareLink;
import com.runtrack.sharing.internal.domain.link.ShareLinkId;
import com.runtrack.sharing.internal.infra.rest.dto.ShareDtos;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** L'émission et la révocation des liens de partage. */
@RestController
@RequestMapping("/api/v1")
class ShareLinkController {

    private final ShareLinks links;

    ShareLinkController(ShareLinks links) {
        this.links = links;
    }

    @PostMapping("/activities/{id}/share-links")
    @ResponseStatus(HttpStatus.CREATED)
    ShareDtos.ShareLinkResponse issue(
            @AuthenticationPrincipal Viewer viewer,
            @PathVariable String id,
            @Valid @RequestBody(required = false) ShareDtos.CreateShareLinkRequest request) {

        Optional<Duration> validFor = Optional.ofNullable(request)
                .map(ShareDtos.CreateShareLinkRequest::validForHours)
                .map(Duration::ofHours);

        ShareLinks.Issued issued = links.issue(requireUser(viewer), ActivityId.of(id), validFor);
        return ShareLinkMapper.toResponse(issued.link(), issued.token().value());
    }

    @GetMapping("/activities/{id}/share-links")
    ShareDtos.ShareLinkListResponse list(@AuthenticationPrincipal Viewer viewer,
            @PathVariable String id) {

        return new ShareDtos.ShareLinkListResponse(
                links.of(requireUser(viewer), ActivityId.of(id)).stream()
                        // Sans le jeton : il n'existe plus, et le rendre ici obligerait à le
                        // stocker en clair — ce que tout le module s'emploie à éviter.
                        .map(link -> ShareLinkMapper.toResponse(link, null))
                        .toList());
    }

    @DeleteMapping("/share-links/{id}")
    ResponseEntity<Void> revoke(@AuthenticationPrincipal Viewer viewer, @PathVariable String id) {
        links.revoke(requireUser(viewer), ShareLinkId.of(id));
        return ResponseEntity.noContent().build();
    }

    private static UserId requireUser(Viewer viewer) {
        if (viewer == null) {
            throw new ForbiddenException("AUTHENTICATION_REQUIRED", "Cette action demande d'être connecté");
        }
        return viewer.userId().orElseThrow(() -> new ForbiddenException(
                "AUTHENTICATION_REQUIRED", "Un lien de partage n'en émet pas d'autres"));
    }

    /** Rassemble ce qui sort, pour que la forme d'un lien soit décrite à un seul endroit. */
    static final class ShareLinkMapper {

        private ShareLinkMapper() {
        }

        static ShareDtos.ShareLinkResponse toResponse(ShareLink link, String clearToken) {
            return new ShareDtos.ShareLinkResponse(
                    link.id().toString(),
                    clearToken,
                    clearToken == null ? null : "/api/v1/shared/" + clearToken,
                    link.createdAt(),
                    link.expiresAt().orElse(null),
                    link.revokedAt().orElse(null),
                    link.viewCount());
        }
    }
}
