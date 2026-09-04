package com.runtrack.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Qui signe les jetons se choisit par une propriété, et le reste de l'application n'en sait
 * rien : elle ne voit qu'un {@link JwtDecoder}.
 */
class JwtDecoderConfigurationTest {

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(LocalKeyPair.class, JwtDecoderConfiguration.class);

    @Test
    void theApplicationSignsItsOwnTokensByDefault() {
        contexts.run(context -> assertThat(context)
                .hasNotFailed()
                .hasSingleBean(JwtDecoder.class)
                .hasBean("localJwtDecoder"));
    }

    /**
     * Le décodeur du realm se construit <b>sans joindre Keycloak</b> : l'adresse ci-dessous ne
     * résout nulle part, et le contexte démarre quand même. C'est ce qui garantit qu'un
     * fournisseur d'identité en retard, ou tombé, n'empêche pas l'API de se lever.
     */
    @Test
    void theRealmSignsThemWhenItIsTheChosenProvider() {
        contexts.withPropertyValues(
                        "runtrack.auth.provider=keycloak",
                        "runtrack.auth.keycloak.issuer-uri=https://keycloak.invalid/realms/runtrack")
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(JwtDecoder.class)
                        .hasBean("keycloakJwtDecoder"));
    }

    /** Une bascule sans adresse de realm accepterait n'importe quel jeton : elle doit échouer. */
    @Test
    void choosingTheRealmWithoutSayingWhereItIsRefusesToStart() {
        contexts.withPropertyValues("runtrack.auth.provider=keycloak")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .hasMessageContaining("issuer-uri"));
    }

    @Test
    void theKeySetLocationIsDeducedFromTheRealm() {
        var properties = new KeycloakProperties("https://id.runtrack.app/realms/runtrack");

        assertThat(properties.jwkSetUri())
                .isEqualTo("https://id.runtrack.app/realms/runtrack/protocol/openid-connect/certs");
    }

    /** L'émetteur local a besoin d'une paire de clés ; celle-ci ne sort pas du test. */
    @Configuration(proxyBeanMethods = false)
    static class LocalKeyPair {

        @Bean
        JWKSource<SecurityContext> jwkSource() throws Exception {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2_048);
            KeyPair pair = generator.generateKeyPair();
            RSAKey key = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey((RSAPrivateKey) pair.getPrivate())
                    .keyID(UUID.randomUUID().toString())
                    .build();
            return new ImmutableJWKSet<>(new JWKSet(key));
        }
    }
}
