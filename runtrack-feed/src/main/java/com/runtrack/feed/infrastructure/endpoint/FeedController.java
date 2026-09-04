package com.runtrack.feed.infrastructure.endpoint;

import com.runtrack.feed.usecases.service.FeedReader;
import com.runtrack.feed.usecases.model.entry.FeedEntry;
import com.runtrack.feed.infrastructure.dto.FeedDtos;
import com.runtrack.platform.openapi.ApiFolders;
import com.runtrack.shared.access.Viewer;
import com.runtrack.shared.error.ForbiddenException;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import com.runtrack.user.UserSummary;
import io.swagger.v3.oas.annotations.Operation;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Le fil d'actualité.
 *
 * <p>Aucun identifiant dans le chemin : un fil est celui de qui le demande, et le seul moyen de le
 * désigner est d'être connecté.
 */
@RestController
@ApiFolders.Feed
@RequestMapping("/feed/v1")
class FeedController {

    private final FeedReader feed;
    private final com.runtrack.course.CourseApi courses;

    FeedController(FeedReader feed, com.runtrack.course.CourseApi courses) {
        this.feed = feed;
        this.courses = courses;
    }

    /** Les statuts d'une course qui court encore : sa trace n'est pas figée. */
    private static final java.util.Set<String> RUNNING = java.util.Set.of("Live", "Paused");

    @Operation(summary = "Lire son fil d'actualité")
    @GetMapping
    FeedDtos.FeedPage read(
            @AuthenticationPrincipal Viewer viewer,
            @RequestParam(required = false) Instant cursor,
            @RequestParam(required = false) Integer limit) {

        FeedReader.Page page = feed.read(requireUser(viewer), Optional.ofNullable(cursor), limit);

        // La projection ne bouge qu'aux événements : démarrage, fin, « j'aime ». Une
        // course en cours y reste donc figée à zéro kilomètre, sans vignette, jusqu'à
        // ce qu'elle se termine — alors qu'elle avance sous les yeux du lecteur.
        //
        // On demande son état du moment à `course`, qui le tient : distance, temps et
        // trace échantillonnée. Une requête, et seulement s'il y a des courses en cours
        // dans la page. Mettre à jour la projection à chaque lot de points coûterait
        // une écriture toutes les sept secondes et par coureur, pour un fil que
        // personne ne regarde la plupart du temps.
        java.util.Set<ActivityId> running = page.entries().stream()
                .filter(entry -> RUNNING.contains(entry.status()))
                .map(FeedEntry::activityId)
                .collect(java.util.stream.Collectors.toSet());
        Map<ActivityId, com.runtrack.course.ActivitySummary> live =
                running.isEmpty() ? Map.of() : courses.summaries(running);

        return new FeedDtos.FeedPage(
                page.entries().stream().map(entry -> toItem(entry, page.authors(), live)).toList(),
                page.nextCursor());
    }

    private static FeedDtos.FeedItem toItem(FeedEntry entry, Map<UserId, UserSummary> authors,
            Map<ActivityId, com.runtrack.course.ActivitySummary> live) {
        return new FeedDtos.FeedItem(
                entry.activityId().toString(),
                authors.containsKey(entry.ownerId()) ? toAuthor(authors.get(entry.ownerId())) : null,
                entry.type(),
                entry.title(),
                entry.status(),
                Optional.ofNullable(live.get(entry.activityId()))
                        .map(com.runtrack.course.ActivitySummary::distanceMeters)
                        .orElseGet(entry::distanceMeters),
                Optional.ofNullable(live.get(entry.activityId()))
                        .map(com.runtrack.course.ActivitySummary::movingTimeSeconds)
                        .orElseGet(entry::movingTimeSeconds),
                entry.startedAt(),
                entry.endedAt().orElse(null),
                entry.likeCount(),
                entry.commentCount(),
                entry.previewPolyline()
                        .or(() -> Optional.ofNullable(live.get(entry.activityId()))
                                .flatMap(com.runtrack.course.ActivitySummary::previewPolyline))
                        .orElse(null));
    }

    private static FeedDtos.AuthorDto toAuthor(UserSummary author) {
        return new FeedDtos.AuthorDto(author.id().toString(), author.handle(),
                author.displayName(), author.avatarUrl().orElse(null));
    }

    private static UserId requireUser(Viewer viewer) {
        if (viewer == null) {
            throw new ForbiddenException("AUTHENTICATION_REQUIRED", "Le fil demande d'être connecté");
        }
        return viewer.userId().orElseThrow(() -> new ForbiddenException(
                "AUTHENTICATION_REQUIRED", "Un lien de partage n'a pas de fil"));
    }
}
