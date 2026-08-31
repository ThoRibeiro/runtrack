package com.runtrack.course.internal.infra.realtime;

import com.runtrack.course.internal.application.port.LiveActivityPublisher;
import com.runtrack.course.internal.domain.live.LiveEvent;
import com.runtrack.platform.realtime.LiveChannel;
import com.runtrack.shared.id.ActivityId;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Le port sortant du direct, branché sur le canal de {@code platform}.
 *
 * <p>Tout ce que cette classe apporte est la traduction : le domaine parle de positions et de
 * statistiques, le canal ne connaît que des noms et des charges utiles. Le report après commit,
 * la troncature du journal et la dégradation gracieuse vivent dans le canal, parce qu'ils sont
 * les mêmes pour tout ce qui se diffuse.
 *
 * <p><b>Pas de Hash {@code live:activity:{id}:state}.</b> Le §4 en prévoit un, avec l'état et
 * les dernières statistiques. Rien ne le lirait : le seul consommateur possible est l'instantané,
 * et celui-ci a déjà chargé la course en base pour vérifier que le spectateur a le droit de la
 * voir. Une seconde copie de l'état que personne ne consulte est une copie qui dérive en silence.
 */
@Component
class RedisLiveActivityPublisher implements LiveActivityPublisher {

    private final LiveChannel channel;
    private final LiveEventCodec codec;

    RedisLiveActivityPublisher(LiveChannel channel, LiveEventCodec codec) {
        this.channel = channel;
        this.codec = codec;
    }

    @Override
    public void publish(ActivityId activityId, List<LiveEvent> events) {
        channel.publish(LiveKeys.events(activityId), events.stream().map(codec::encode).toList());
    }

    @Override
    public void closeStream(ActivityId activityId) {
        channel.close(LiveKeys.events(activityId));
    }
}
