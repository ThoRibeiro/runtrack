package com.runtrack.course.internal.infra.realtime;

import com.runtrack.course.internal.domain.live.LiveEvent;
import com.runtrack.shared.id.ActivityId;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * La porte d'entrée du direct pour le contrôleur SSE.
 *
 * <p>Elle existe pour que l'ordre d'établissement d'une connexion soit écrit à un seul endroit,
 * parce qu'il n'est pas interchangeable :
 * <ol>
 *   <li>on <b>s'abonne d'abord</b> — tout ce qui survient à partir de maintenant est capturé ;</li>
 *   <li>on lit <b>ensuite</b> l'instantané, ou la relecture depuis le {@code Last-Event-ID} ;</li>
 *   <li>on l'insère <b>en tête</b> de la file du spectateur, devant ce qui s'y est déjà glissé ;</li>
 *   <li>on démarre alors seulement le pompage.</li>
 * </ol>
 * Inverser 1 et 2 laisserait un trou : les événements des quelques millisecondes de la lecture
 * en base seraient perdus, sans que personne ne s'en aperçoive jamais.
 */
@Component
public class LiveBroadcast {

    private static final Logger LOG = LoggerFactory.getLogger(LiveBroadcast.class);

    private final LiveEmitterRegistry registry;
    private final LiveEventLog log;
    private final LiveEventCodec codec;
    private final RealtimeProperties properties;

    LiveBroadcast(LiveEmitterRegistry registry, LiveEventLog log, LiveEventCodec codec,
            RealtimeProperties properties) {
        this.registry = registry;
        this.log = log;
        this.codec = codec;
        this.properties = properties;
    }

    /** Un spectateur sur une course en cours : instantané puis direct, jusqu'à ce qu'il parte. */
    public SseEmitter follow(ActivityId activityId, Optional<String> lastEventId,
            Supplier<List<LiveEvent>> snapshot) {

        var emitter = new SseEmitter(properties.emitterTimeout().toMillis());
        LiveSubscriber subscriber = LiveSubscriber.attachedTo(emitter, properties.subscriberQueueCapacity());

        registry.register(activityId, subscriber);
        emitter.onCompletion(() -> release(activityId, subscriber));
        emitter.onTimeout(() -> release(activityId, subscriber));
        emitter.onError(failure -> release(activityId, subscriber));

        if (!subscriber.offerBacklog(backlogFor(activityId, lastEventId, snapshot))) {
            // L'instantané n'a pas tenu : ce spectateur commencerait sur un tracé amputé sans
            // jamais le savoir. Mieux vaut le renvoyer se reconnecter.
            LOG.warn("Instantané trop volumineux pour la file du spectateur, course {}", activityId);
            registry.deregister(activityId, subscriber);
            subscriber.complete();
            return emitter;
        }
        subscriber.startPumping();
        return emitter;
    }

    /**
     * Une course terminée : l'instantané, puis on raccroche.
     *
     * <p>Rien ne sera plus publié. Garder la connexion ouverte occuperait un abonnement Dragonfly
     * et une file pour ne jamais rien y mettre, et laisserait le client attendre un direct qui
     * n'existe plus au lieu de basculer sur l'affichage d'une course finie.
     */
    public SseEmitter replayAndClose(Supplier<List<LiveEvent>> snapshot) {
        var emitter = new SseEmitter(properties.emitterTimeout().toMillis());
        try {
            for (LiveEvent event : snapshot.get()) {
                RecordedEvent recorded = codec.encodeForSse(event);
                emitter.send(SseEmitter.event().name(recorded.kind()).data(recorded.payload()));
            }
            emitter.complete();
        } catch (Exception disconnected) {
            emitter.completeWithError(disconnected);
        }
        return emitter;
    }

    /**
     * Ce que le spectateur reçoit avant le direct : la relecture s'il peut reprendre, l'instantané
     * sinon. Jamais les deux — ce serait le même tracé deux fois.
     */
    private List<RecordedEvent> backlogFor(ActivityId activityId, Optional<String> lastEventId,
            Supplier<List<LiveEvent>> snapshot) {

        return lastEventId
                .flatMap(eventId -> log.replayAfter(activityId, eventId))
                .orElseGet(() -> snapshot.get().stream().map(codec::encodeForSse).toList());
    }

    /** L'émetteur s'est refermé : on range le spectateur sans essayer de le refermer encore. */
    private void release(ActivityId activityId, LiveSubscriber subscriber) {
        subscriber.detach();
        registry.deregister(activityId, subscriber);
    }
}
