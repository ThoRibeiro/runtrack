package com.runtrack.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.runtrack.RunTrackApplication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;

/**
 * La chaîne complète du §5.4, contre un vrai serveur.
 *
 * <p>Ce test existe parce que MockMvc n'exécute pas les réacheminements : il note la cible et
 * s'arrête. Or c'est précisément le réacheminement qui porte l'idée — {@code sharing} résout le
 * jeton, {@code course} sert la course, et ni l'un ni l'autre ne connaît l'existence du second.
 * Le vérifier demande un conteneur de servlets, pas une simulation.
 *
 * <p>Les conteneurs sont ceux de {@link ApiIntegrationTest}, démarrés une fois pour la JVM : ce
 * test a besoin d'un serveur sur un port réel, pas d'une seconde base.
 */
@SpringBootTest(classes = RunTrackApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SharedActivityIT {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper json;

    private final HttpClient http = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void sharedContainers(DynamicPropertyRegistry registry) {
        ApiIntegrationTest.POSTGRES.start();
        ApiIntegrationTest.DRAGONFLY.start();
        registry.add("spring.datasource.url", ApiIntegrationTest.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", ApiIntegrationTest.POSTGRES::getUsername);
        registry.add("spring.datasource.password", ApiIntegrationTest.POSTGRES::getPassword);
        registry.add("spring.data.redis.host", ApiIntegrationTest.DRAGONFLY::getHost);
        registry.add("spring.data.redis.port", () -> ApiIntegrationTest.DRAGONFLY.getMappedPort(6379));
    }

    private record Account(String token) {
    }

    /** Un client HTTP nu plutôt qu'un client Spring : ce test vérifie un serveur, pas un template. */
    private HttpResponse<String> send(String method, String path, String body, String bearer) {
        try {
            var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .timeout(Duration.ofSeconds(15))
                    .method(method, body == null
                            ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(body));
            if (bearer != null) {
                builder = builder.header("Authorization", "Bearer " + bearer);
            }
            return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private Account newAccount() {
        String handle = "s" + System.nanoTime() % 1_000_000;
        send("POST", "/api/v1/auth/signup", """
                {"handle":"%s","email":"%s@example.com","displayName":"Coureur",
                 "password":"correcthorsebattery"}
                """.formatted(handle, handle), null);

        HttpResponse<String> login = send("POST", "/api/v1/auth/login", """
                {"email":"%s@example.com","password":"correcthorsebattery"}
                """.formatted(handle), null);
        return new Account(json.readTree(login.body()).get("accessToken").asText());
    }

    private String startPrivateRun(Account owner) {
        HttpResponse<String> started = send("POST", "/api/v1/activities", """
                {"type":"RUN","title":"Sortie du matin","visibility":"PRIVATE"}
                """, owner.token());
        return json.readTree(started.body()).get("id").asText();
    }

    private String shareLinkFor(Account owner, String runId) {
        HttpResponse<String> created = send("POST",
                "/api/v1/activities/" + runId + "/share-links", "{}", owner.token());
        return json.readTree(created.body()).get("token").asText();
    }

    /**
     * Le cœur du §3 : un lien ouvre une course privée <b>sans aucune authentification</b>.
     *
     * <p>Et c'est {@code ActivityAccessPolicy} qui l'autorise, à partir du {@code ShareLinkHolder}
     * posé par le filtre — {@code course} n'a pas de règle spéciale pour le partage.
     */
    @Test
    void aLinkOpensOnePrivateRunWithoutAnyAccount() {
        Account marie = newAccount();
        String runId = startPrivateRun(marie);
        String token = shareLinkFor(marie, runId);

        HttpResponse<String> shared = send("GET", "/api/v1/shared/" + token, null, null);

        assertThat(shared.statusCode()).isEqualTo(200);
        assertThat(json.readTree(shared.body()).get("id").asText()).isEqualTo(runId);
        assertThat(json.readTree(shared.body()).get("title").asText()).isEqualTo("Sortie du matin");
    }

    /** Le direct d'une course partagée emprunte le même chemin, et arrive jusqu'au SSE. */
    @Test
    void aLinkAlsoOpensTheLiveStream() throws Exception {
        Account marie = newAccount();
        String runId = startPrivateRun(marie);
        String token = shareLinkFor(marie, runId);

        var request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/v1/shared/" + token + "/stream"))
                .header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE)
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<java.io.InputStream> response =
                http.send(request, HttpResponse.BodyHandlers.ofInputStream());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(readUntilStats(response.body())).contains("event:status").contains("event:stats");
    }

    /** Un porteur de lien lit, il ne participe pas : le §3 lui donne un accès en lecture. */
    @Test
    void aLinkHolderCannotLikeTheRunTheyAreReading() {
        Account marie = newAccount();
        String runId = startPrivateRun(marie);
        String token = shareLinkFor(marie, runId);

        HttpResponse<String> refused = send("POST", "/api/v1/shared/" + token + "/likes", "", null);

        assertThat(refused.statusCode()).isEqualTo(403);
    }

    @Test
    void anUnknownTokenOpensNothing() {
        assertThat(send("GET", "/api/v1/shared/jeton-invente", null, null).statusCode())
                .isEqualTo(404);
    }

    /** Lit le flux jusqu'à l'instantané, sans attendre qu'il se ferme : un SSE ne se ferme pas. */
    private static String readUntilStats(java.io.InputStream body) throws Exception {
        var received = new StringBuilder();
        var buffer = new byte[1_024];
        Instant deadline = Instant.now().plusSeconds(15);
        while (Instant.now().isBefore(deadline) && !received.toString().contains("event:stats")) {
            int read = body.read(buffer);
            if (read < 0) {
                break;
            }
            received.append(new String(buffer, 0, read, java.nio.charset.StandardCharsets.UTF_8));
        }
        return received.toString();
    }
}
