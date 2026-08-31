package com.runtrack.course.internal.infra.rest;

import static com.runtrack.course.internal.infra.rest.Principals.asViewer;
import static com.runtrack.course.internal.infra.rest.Principals.requireUser;

import com.runtrack.course.internal.application.ActivityLifecycle;
import com.runtrack.course.internal.application.ActivityQueries;
import com.runtrack.course.internal.domain.activity.Activity;
import com.runtrack.course.internal.domain.activity.ActivityType;
import com.runtrack.course.internal.infra.rest.dto.ActivityDtos;
import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.access.Viewer;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import com.runtrack.social.SocialApi;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Le cycle de vie et la lecture des courses.
 *
 * <p>Aucune règle de visibilité ici : chaque lecture passe par {@link ActivityQueries}, qui
 * délègue à la politique du domaine.
 */
@RestController
@RequestMapping("/api/v1")
class ActivityController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ActivityLifecycle lifecycle;
    private final ActivityQueries queries;
    private final SocialApi social;

    ActivityController(ActivityLifecycle lifecycle, ActivityQueries queries, SocialApi social) {
        this.lifecycle = lifecycle;
        this.queries = queries;
        this.social = social;
    }

    @PostMapping("/activities")
    @ResponseStatus(HttpStatus.CREATED)
    ActivityDtos.ActivityResponse start(
            @AuthenticationPrincipal Viewer viewer,
            @Valid @RequestBody ActivityDtos.StartActivityRequest request) {

        Activity activity = lifecycle.start(
                requireUser(viewer),
                ActivityType.valueOf(request.type()),
                request.title(),
                request.description(),
                AudienceScope.valueOf(request.visibility()),
                request.deviceTime());
        return ActivityMapper.toResponse(activity, queries.statsOf(activity));
    }

    @PostMapping("/activities/{id}/pause")
    ResponseEntity<Void> pause(@AuthenticationPrincipal Viewer viewer, @PathVariable String id) {
        lifecycle.pause(requireUser(viewer), ActivityId.of(id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/activities/{id}/resume")
    ResponseEntity<Void> resume(@AuthenticationPrincipal Viewer viewer, @PathVariable String id) {
        lifecycle.resume(requireUser(viewer), ActivityId.of(id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/activities/{id}/finish")
    ResponseEntity<Void> finish(@AuthenticationPrincipal Viewer viewer, @PathVariable String id) {
        lifecycle.finish(requireUser(viewer), ActivityId.of(id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/activities/{id}/discard")
    ResponseEntity<Void> discard(@AuthenticationPrincipal Viewer viewer, @PathVariable String id) {
        lifecycle.discard(requireUser(viewer), ActivityId.of(id));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/activities/{id}")
    ActivityDtos.ActivityResponse update(
            @AuthenticationPrincipal Viewer viewer,
            @PathVariable String id,
            @Valid @RequestBody ActivityDtos.UpdateActivityRequest request) {

        Activity activity = lifecycle.rename(
                requireUser(viewer), ActivityId.of(id), request.title(), request.description());
        return ActivityMapper.toResponse(activity, queries.statsOf(activity));
    }

    @PutMapping("/activities/{id}/visibility")
    ActivityDtos.ActivityResponse changeVisibility(
            @AuthenticationPrincipal Viewer viewer,
            @PathVariable String id,
            @Valid @RequestBody ActivityDtos.ChangeVisibilityRequest request) {

        Activity activity = lifecycle.changeScope(
                requireUser(viewer), ActivityId.of(id), AudienceScope.valueOf(request.visibility()));
        return ActivityMapper.toResponse(activity, queries.statsOf(activity));
    }

    @DeleteMapping("/activities/{id}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal Viewer viewer, @PathVariable String id) {
        lifecycle.delete(requireUser(viewer), ActivityId.of(id));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/activities/{id}")
    ActivityDtos.ActivityResponse byId(@AuthenticationPrincipal Viewer viewer, @PathVariable String id) {
        Activity activity = queries.require(asViewer(viewer), ActivityId.of(id));
        return ActivityMapper.toResponse(activity, queries.statsOf(activity));
    }

    @GetMapping("/users/{id}/activities")
    ActivityDtos.ActivityPage ofUser(
            @AuthenticationPrincipal Viewer viewer,
            @PathVariable String id,
            @RequestParam(required = false) Instant cursor,
            @RequestParam(required = false) Integer limit) {

        List<Activity> found = queries.ofOwner(
                asViewer(viewer), UserId.of(id), Optional.ofNullable(cursor), pageSize(limit));
        return toPage(found);
    }

    /** Les courses en cours des comptes suivis : l'écran « en direct ». */
    @GetMapping("/activities/live")
    ActivityDtos.ActivityPage live(@AuthenticationPrincipal Viewer viewer) {
        UserId reader = requireUser(viewer);
        return toPage(queries.liveOf(viewer, social.acceptedFolloweeIds(reader)));
    }

    private ActivityDtos.ActivityPage toPage(List<Activity> found) {
        List<ActivityDtos.ActivityResponse> items = found.stream()
                .map(activity -> ActivityMapper.toResponse(activity, queries.statsOf(activity)))
                .toList();
        Instant next = found.isEmpty() ? null : found.getLast().startedAt();
        return new ActivityDtos.ActivityPage(items, next);
    }

    private static int pageSize(Integer requested) {
        if (requested == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.clamp(requested, 1, MAX_PAGE_SIZE);
    }
}
