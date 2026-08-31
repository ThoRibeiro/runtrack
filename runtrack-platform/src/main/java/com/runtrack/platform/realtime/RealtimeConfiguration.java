package com.runtrack.platform.realtime;

import java.util.concurrent.Executors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;

/** Le conteneur qui consomme les Streams Dragonfly du direct. */
@Configuration
@EnableConfigurationProperties(RealtimeProperties.class)
class RealtimeConfiguration {

    /**
     * Un fil virtuel par abonnement.
     *
     * <p>Chaque abonnement passe l'essentiel de son temps bloqué dans un {@code XREAD} : c'est
     * exactement le profil pour lequel les fils virtuels existent, et un pool de fils de
     * plateforme y plafonnerait le nombre de courses diffusables par instance.
     */
    @Bean(destroyMethod = "stop")
    StreamMessageListenerContainer<String, MapRecord<String, String, String>> liveStreamContainer(
            RedisConnectionFactory connections, RealtimeProperties properties) {

        var options = StreamMessageListenerContainerOptions.builder()
                .pollTimeout(properties.pollTimeout())
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .serializer(new StringRedisSerializer())
                .build();

        var container = StreamMessageListenerContainer.create(connections, options);
        container.start();
        return container;
    }
}
