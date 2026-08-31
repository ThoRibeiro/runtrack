package com.runtrack.course.internal.infra.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.runtrack.course.internal.domain.live.LiveEvent;
import com.runtrack.shared.id.ActivityId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Ce que le publieur fait quand Dragonfly ne répond pas.
 *
 * <p>La règle du port : aucune panne du direct ne remonte à l'appelant. Les points sont en base ;
 * que personne ne les regarde en temps réel est un moindre mal, perdre l'enregistrement n'en
 * serait pas un.
 */
class RedisLiveActivityPublisherTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-29T08:00:00Z"), ZoneOffset.UTC);

    private SimpleMeterRegistry meters;
    private RedisLiveActivityPublisher publisher;
    private ActivityId run;

    @BeforeEach
    void setUp() {
        meters = new SimpleMeterRegistry();
        publisher = new RedisLiveActivityPublisher(
                new UnreachableRedis(),
                new LiveEventCodec(new ObjectMapper()),
                new RealtimeProperties(null, null, null, null, null, null),
                meters);
        run = ActivityId.generate(CLOCK, new Random(3));
    }

    private static LiveEvent aStatus() {
        return new LiveEvent.Status("Live", Instant.parse("2026-08-29T08:00:00Z"));
    }

    @Test
    void anUnreachableDragonflyNeverFailsTheIngestion() {
        assertThatCode(() -> publisher.publish(run, List.of(aStatus()))).doesNotThrowAnyException();

        assertThat(meters.counter("runtrack.live.events.failed").count()).isEqualTo(1);
        assertThat(meters.counter("runtrack.live.events.published").count()).isZero();
    }

    @Test
    void closingAStreamThatCannotBeReachedIsNotAnError() {
        assertThatCode(() -> publisher.closeStream(run)).doesNotThrowAnyException();
    }

    /** Rien à dire, rien à écrire : pas d'aller-retour pour un lot entièrement rejeté. */
    @Test
    void anEmptyBroadcastTouchesNothing() {
        publisher.publish(run, List.of());

        assertThat(meters.counter("runtrack.live.events.failed").count()).isZero();
    }
}
