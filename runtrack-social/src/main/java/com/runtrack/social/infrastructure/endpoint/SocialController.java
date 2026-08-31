package com.runtrack.social.infrastructure.endpoint;

import com.runtrack.shared.access.Viewer;
import com.runtrack.shared.error.ForbiddenException;
import com.runtrack.shared.id.UserId;
import com.runtrack.social.usecases.service.SocialGraph;
import com.runtrack.social.usecases.model.graph.Follow;
import com.runtrack.social.infrastructure.dto.SocialDtos;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Abonnements, demandes et blocages. Aucune règle ici : tout est dans le cas d'usage. */
@RestController
@RequestMapping("/api/v1")
class SocialController {

    private final SocialGraph graph;

    SocialController(SocialGraph graph) {
        this.graph = graph;
    }

    @PostMapping("/users/{id}/follow")
    SocialDtos.FollowResponse follow(@AuthenticationPrincipal Viewer viewer, @PathVariable String id) {
        Follow follow = graph.follow(requireUser(viewer), UserId.of(id));
        return new SocialDtos.FollowResponse(follow.status().name(), !follow.isAccepted());
    }

    @DeleteMapping("/users/{id}/follow")
    ResponseEntity<Void> unfollow(@AuthenticationPrincipal Viewer viewer, @PathVariable String id) {
        graph.unfollow(requireUser(viewer), UserId.of(id));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/{id}/followers")
    SocialDtos.UserIdList followers(@PathVariable String id) {
        return SocialDtos.UserIdList.of(graph.followers(UserId.of(id)));
    }

    @GetMapping("/users/{id}/following")
    SocialDtos.UserIdList following(@PathVariable String id) {
        return SocialDtos.UserIdList.of(graph.followees(UserId.of(id)));
    }

    @GetMapping("/me/follow-requests")
    List<SocialDtos.PendingRequest> pendingRequests(@AuthenticationPrincipal Viewer viewer) {
        return graph.pendingRequests(requireUser(viewer)).stream()
                .map(follow -> new SocialDtos.PendingRequest(
                        follow.id().toString(), follow.followerId().toString(), follow.requestedAt()))
                .toList();
    }

    @PostMapping("/follow-requests/{id}/accept")
    ResponseEntity<Void> accept(@AuthenticationPrincipal Viewer viewer, @PathVariable String id) {
        graph.acceptRequest(requireUser(viewer), UUID.fromString(id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/follow-requests/{id}/reject")
    ResponseEntity<Void> reject(@AuthenticationPrincipal Viewer viewer, @PathVariable String id) {
        graph.rejectRequest(requireUser(viewer), UUID.fromString(id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/{id}/block")
    ResponseEntity<Void> block(@AuthenticationPrincipal Viewer viewer, @PathVariable String id) {
        graph.block(requireUser(viewer), UserId.of(id));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/{id}/block")
    ResponseEntity<Void> unblock(@AuthenticationPrincipal Viewer viewer, @PathVariable String id) {
        graph.unblock(requireUser(viewer), UserId.of(id));
        return ResponseEntity.noContent().build();
    }

    private static UserId requireUser(Viewer viewer) {
        if (viewer == null) {
            throw new ForbiddenException("AUTHENTICATION_REQUIRED", "Cette action demande d'être connecté");
        }
        return viewer.userId().orElseThrow(() -> new ForbiddenException(
                "AUTHENTICATION_REQUIRED", "Un lien de partage ne donne pas accès au graphe social"));
    }
}
