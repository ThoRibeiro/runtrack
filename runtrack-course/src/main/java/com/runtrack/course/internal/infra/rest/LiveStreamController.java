package com.runtrack.course.internal.infra.rest;

import static com.runtrack.course.internal.infra.rest.Principals.asViewer;

import com.runtrack.course.internal.application.ActivityQueries;
import com.runtrack.course.internal.application.LiveActivityStream;
import com.runtrack.course.internal.domain.activity.Activity;
import com.runtrack.course.internal.infra.realtime.LiveEventCodec;
import com.runtrack.course.internal.infra.realtime.LiveKeys;
import com.runtrack.platform.realtime.LiveChannel;
import com.runtrack.platform.realtime.PublishedEvent;
import com.runtrack.shared.access.Viewer;
import com.runtrack.shared.id.ActivityId;
import java.util.List;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Le suivi d'une course en direct, en SSE.
 *
 * <p>Aucune règle de visibilité ici non plus : {@link ActivityQueries#require} tranche, comme
 * pour n'importe quelle lecture. Le direct n'est pas une porte dérobée — une course privée
 * répond « introuvable » sur ce chemin comme sur les autres.
 */
@RestController
@RequestMapping("/api/v1")
class LiveStreamController {

    private final ActivityQueries queries;
    private final LiveActivityStream stream;
    private final LiveEventCodec codec;
    private final LiveChannel channel;

    LiveStreamController(ActivityQueries queries, LiveActivityStream stream,
            LiveEventCodec codec, LiveChannel channel) {
        this.queries = queries;
        this.stream = stream;
        this.codec = codec;
        this.channel = channel;
    }

    @GetMapping(path = "/activities/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter follow(
            @AuthenticationPrincipal Viewer viewer,
            @PathVariable String id,
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId) {

        Activity activity = queries.require(asViewer(viewer), ActivityId.of(id));

        if (activity.status().isTerminal()) {
            // Plus rien ne sera publié : on rend l'état final et on raccroche, plutôt que de
            // laisser le client attendre un direct qui n'existe plus.
            return channel.sendOnce(() -> snapshotOf(activity));
        }
        return channel.subscribe(
                LiveKeys.events(activity.id()), Optional.ofNullable(lastEventId),
                () -> snapshotOf(activity));
    }

    /** L'instantané, traduit une fois pour toutes en événements prêts à partir. */
    private List<PublishedEvent> snapshotOf(Activity activity) {
        return stream.snapshotOf(activity).stream().map(codec::encode).toList();
    }
}
