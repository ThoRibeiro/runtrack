package com.runtrack.user.infrastructure.endpoint;

import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.access.Viewer;
import com.runtrack.shared.error.ForbiddenException;
import com.runtrack.shared.id.UserId;
import com.runtrack.user.usecases.service.UserAccounts;
import com.runtrack.user.usecases.model.profile.Handle;
import com.runtrack.user.infrastructure.dto.ProfileDtos;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Les points d'entrée HTTP du profil.
 *
 * <p>Aucune règle métier ici : le contrôleur traduit une requête en appel de cas d'usage
 * et une réponse en DTO. Le {@link Viewer} lui est fourni par le filtre de sécurité du
 * module {@code auth}, ce qui lui évite de dépendre de ce module.
 */
@RestController
@RequestMapping("/api/v1")
class UserController {

    private final UserAccounts accounts;

    UserController(UserAccounts accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/users/me")
    ProfileDtos.MyProfile me(@AuthenticationPrincipal Viewer viewer) {
        return ProfileMapper.toMyProfile(accounts.byId(requireUser(viewer)));
    }

    @PatchMapping("/users/me")
    ProfileDtos.MyProfile updateMe(
            @AuthenticationPrincipal Viewer viewer,
            @Valid @RequestBody ProfileDtos.UpdateProfileRequest request) {

        UserId id = requireUser(viewer);
        accounts.updateProfile(id, request.displayName(), request.bio(), request.avatarUrl());
        return ProfileMapper.toMyProfile(accounts.byId(id));
    }

    @PutMapping("/users/me/handle")
    ProfileDtos.MyProfile changeHandle(
            @AuthenticationPrincipal Viewer viewer,
            @Valid @RequestBody ProfileDtos.ChangeHandleRequest request) {

        UserId id = requireUser(viewer);
        accounts.changeHandle(id, new Handle(request.handle()));
        return ProfileMapper.toMyProfile(accounts.byId(id));
    }

    @PutMapping("/users/me/avatar")
    ProfileDtos.MyProfile changeAvatar(
            @AuthenticationPrincipal Viewer viewer,
            @Valid @RequestBody ProfileDtos.ChangeAvatarRequest request) {

        UserId id = requireUser(viewer);
        accounts.changeAvatar(id, request.avatarUrl());
        return ProfileMapper.toMyProfile(accounts.byId(id));
    }

    @PutMapping("/users/me/visibility")
    ProfileDtos.MyProfile changeVisibility(
            @AuthenticationPrincipal Viewer viewer,
            @Valid @RequestBody ProfileDtos.ChangeScopeRequest request) {

        UserId id = requireUser(viewer);
        accounts.changeAccountScope(id, AudienceScope.valueOf(request.accountScope()));
        return ProfileMapper.toMyProfile(accounts.byId(id));
    }

    @GetMapping("/users/me/physiology")
    ProfileDtos.PhysiologyPayload physiology(@AuthenticationPrincipal Viewer viewer) {
        return ProfileMapper.toPayload(accounts.byId(requireUser(viewer)).physiology());
    }

    @PutMapping("/users/me/physiology")
    ProfileDtos.PhysiologyPayload updatePhysiology(
            @AuthenticationPrincipal Viewer viewer,
            @Valid @RequestBody ProfileDtos.PhysiologyPayload payload) {

        UserId id = requireUser(viewer);
        accounts.recordPhysiology(id, ProfileMapper.toPhysiology(payload));
        return ProfileMapper.toPayload(accounts.byId(id).physiology());
    }

    @DeleteMapping("/users/me")
    ResponseEntity<Void> deleteMe(@AuthenticationPrincipal Viewer viewer) {
        accounts.delete(requireUser(viewer));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/{handle}")
    ProfileDtos.PublicProfile byHandle(@PathVariable String handle) {
        return ProfileMapper.toPublicProfile(accounts.byHandle(new Handle(handle)));
    }

    @GetMapping("/users")
    List<ProfileDtos.PublicProfile> search(@RequestParam("search") String search) {
        return accounts.search(search).stream().map(ProfileMapper::toPublicProfile).toList();
    }

    private static UserId requireUser(Viewer viewer) {
        if (viewer == null) {
            throw new ForbiddenException("AUTHENTICATION_REQUIRED", "Cette action demande d'être connecté");
        }
        return viewer.userId().orElseThrow(() -> new ForbiddenException(
                "AUTHENTICATION_REQUIRED", "Un lien de partage ne donne pas accès à un profil"));
    }
}
