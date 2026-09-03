package com.runtrack.platform.realtime;

import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;

/** Le conteneur qui consomme les Streams Dragonfly du direct. */
@Configuration
@EnableConfigurationProperties(RealtimeProperties.class)
class RealtimeConfiguration {

    /**
     * Une fabrique de connexions à part, et surtout <b>non partagée</b>.
     *
     * <p>Ailleurs, Lettuce multiplexe toutes les commandes sur une connexion unique — c'est ce
     * qui permet de se passer d'un pool. Mais le multiplexage ne vaut pas pour les commandes
     * <em>bloquantes</em> : un {@code XREAD BLOCK} occupe la connexion jusqu'à ce qu'il rende la
     * main. Sur la connexion partagée, les lectures de tous les abonnements se mettent donc en
     * file — le deuxième attend le premier, le troisième attend les deux — et le délai de
     * commande de Lettuce finit par tomber. C'est la cause des {@code RedisCommandTimeoutException}
     * qui revenaient dès que plusieurs courses étaient suivies en même temps ; baisser le
     * {@code poll-timeout} ne faisait que déplacer le seuil.
     *
     * <p>{@code defaultCandidate = false} : le bean reste injectable par son qualificateur, mais
     * sort de la résolution par type. Sans cela, il rendrait ambiguë chaque injection de
     * {@link RedisConnectionFactory} ailleurs dans l'application.
     */
    @Bean(destroyMethod = "destroy", defaultCandidate = false)
    LettuceConnectionFactory streamConnections(LettuceConnectionFactory shared) {
        var dedicated = new LettuceConnectionFactory(
                shared.getStandaloneConfiguration(), shared.getClientConfiguration());
        dedicated.setShareNativeConnection(false);
        dedicated.afterPropertiesSet();
        return dedicated;
    }

    /**
     * Un fil virtuel par abonnement.
     *
     * <p>Chaque abonnement passe l'essentiel de son temps bloqué dans un {@code XREAD} : c'est
     * exactement le profil pour lequel les fils virtuels existent, et un pool de fils de
     * plateforme y plafonnerait le nombre de courses diffusables par instance.
     */
    @Bean(destroyMethod = "stop")
    StreamMessageListenerContainer<String, MapRecord<String, String, String>> liveStreamContainer(
            @Qualifier("streamConnections") RedisConnectionFactory connections,
            RealtimeProperties properties) {

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
