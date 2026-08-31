package com.runtrack.notification.internal.infra.push;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Rend les réglages du push injectables ; les envoyeurs se choisissent par condition. */
@Configuration
@EnableConfigurationProperties(PushProperties.class)
class PushConfiguration {
}
