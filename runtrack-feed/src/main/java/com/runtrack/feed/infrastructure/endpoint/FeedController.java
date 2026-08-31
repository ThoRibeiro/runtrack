package com.runtrack.feed.infrastructure.endpoint;

import com.runtrack.feed.usecases.service.FeedReader;
import com.runtrack.feed.usecases.model.entry.FeedEntry;
import com.runtrack.feed.infrastructure.dto.FeedDtos;
import com.runtrack.shared.access.Viewer;
import com.runtrack.shared.error.ForbiddenException;
import com.runtrack.shared.id.UserId;
import com.runtrack.user.UserSummary;
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
@RequestMapping("/api/v1")
class FeedController {

    private final FeedReader feed;

    FeedController(FeedReader feed) {
        this.feed = feed;
    }

    @GetMapping("/feed")
    FeedDtos.FeedPage read(
            @AuthenticationPrincipal Viewer viewer,
            @RequestParam(required = false) Instant cursor,
            @RequestParam(required = false) Integer limit) {

        FeedReader.Page page = feed.read(requireUser(viewer), Optional.ofNullable(cursor), limit);
        return new FeedDtos.FeedPage(
                page.entries().stream().map(entry -> toItem(entry, page.authors())).toList(),
                page.nextCursor());
    }

    private static FeedDtos.FeedItem toItem(FeedEntry entry, Map<UserId, UserSummary> authors) {
        return new FeedDtos.FeedItem(
                entry.activityId().toString(),
                authors.containsKey(entry.ownerId()) ? toAuthor(authors.get(entry.ownerId())) : null,
                entry.type(),
                entry.title(),
                entry.status(),
                entry.distanceMeters(),
                entry.movingTimeSeconds(),
                entry.startedAt(),
                entry.endedAt().orElse(null),
                entry.likeCount(),
                entry.commentCount());
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
