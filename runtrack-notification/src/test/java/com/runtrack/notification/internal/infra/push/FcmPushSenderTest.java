package com.runtrack.notification.internal.infra.push;

import static org.assertj.core.api.Assertions.assertThat;

import com.runtrack.notification.internal.application.port.PushSender;
import com.runtrack.notification.internal.domain.push.DevicePlatform;
import com.runtrack.notification.internal.domain.push.DeviceToken;
import com.runtrack.notification.internal.domain.push.PushMessage;
import com.runtrack.shared.id.UserId;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * L'envoyeur Firebase, contre un vrai serveur HTTP local.
 *
 * <p>Un serveur plutôt qu'un client simulé : les appels d'un lot partent en parallèle sur des fils
 * virtuels, et un simulateur de requêtes attendues dans l'ordre n'aurait rien prouvé de ce
 * parallélisme — sinon qu'il gêne le test.
 *
 * <p>Le jeton d'accès est bouchonné : c'est la seule partie qui exige un vrai compte de service, et
 * c'est précisément pourquoi elle est derrière une interface.
 */
class FcmPushSenderTest {

    private static final UserId MARIE = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000001"));
    private static final PushMessage MESSAGE =
            new PushMessage("Marie court", "Suivez-la", "/activities/abc/live");

    private HttpServer server;
    private final List<String> received = new CopyOnWriteArrayList<>();
    private final List<String> authorizations = new CopyOnWriteArrayList<>();

    /** Décide, jeton par jeton, ce que Firebase répond. */
    private Function<String, Reply> firebase = body -> new Reply(200, "{}");

    private record Reply(int status, String body) {
    }

    @BeforeEach
    void startFirebase() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", this::respond);
        server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
        server.start();
    }

    @AfterEach
    void stopFirebase() {
        server.stop(0);
    }

    private void respond(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        received.add(body);
        authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));

        Reply reply = firebase.apply(body);
        byte[] payload = reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(reply.status(), payload.length);
        try (var out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    private FcmPushSender senderWithBatchesOf(int batchSize) {
        var properties = new PushProperties(
                "runtrack-test",
                "classpath:unused.json",
                "http://localhost:" + server.getAddress().getPort(),
                batchSize,
                null);
        return new FcmPushSender(RestClient.builder(), () -> "jeton-de-service", properties);
    }

    private static List<DeviceToken> devices(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(index -> new DeviceToken(
                        "token-" + index, MARIE, DevicePlatform.ANDROID, Instant.EPOCH))
                .toList();
    }

    @Test
    void sendsTheTitleTheBodyAndTheDeepLinkToEachDevice() {
        PushSender.Result result = senderWithBatchesOf(500).send(devices(1), MESSAGE);

        assertThat(result.delivered()).isEqualTo(1);
        assertThat(received).singleElement().satisfies(body -> {
            assertThat(body).contains("\"token\":\"token-1\"");
            assertThat(body).contains("\"title\":\"Marie court\"");
            // Le lien profond voyage en data : c'est ce que l'application lit à l'ouverture.
            assertThat(body).contains("\"deepLink\":\"/activities/abc/live\"");
        });
        assertThat(authorizations).containsExactly("Bearer jeton-de-service");
    }

    /** Le découpage du §7 : au-delà du lot, on repart pour un tour, jamais un appel en série. */
    @Test
    void everyDeviceIsReachedEvenAcrossSeveralBatches() {
        PushSender.Result result = senderWithBatchesOf(2).send(devices(5), MESSAGE);

        assertThat(result.delivered()).isEqualTo(5);
        assertThat(received).hasSize(5);
    }

    /** Un jeton que Firebase ne connaît plus remonte pour être effacé. */
    @Test
    void anUnregisteredTokenComesBackAsInvalid() {
        firebase = body -> body.contains("token-2")
                ? new Reply(404, "{\"error\":{\"details\":[{\"errorCode\":\"UNREGISTERED\"}]}}")
                : new Reply(200, "{}");

        PushSender.Result result = senderWithBatchesOf(500).send(devices(3), MESSAGE);

        assertThat(result.invalidTokens()).containsExactly("token-2");
        assertThat(result.delivered()).isEqualTo(2);
    }

    /**
     * Une panne passagère n'efface rien.
     *
     * <p>Purger sur un 500 couperait définitivement des appareils parfaitement joignables, le jour
     * précis où Firebase a un incident — c'est-à-dire au pire moment.
     */
    @Test
    void aTransientFailurePurgesNothing() {
        firebase = body -> new Reply(503, "{\"error\":{\"status\":\"UNAVAILABLE\"}}");

        PushSender.Result result = senderWithBatchesOf(500).send(devices(2), MESSAGE);

        assertThat(result.invalidTokens()).isEmpty();
        assertThat(result.delivered()).isZero();
    }

    @Test
    void anEmptyDeviceListNeverTouchesTheNetwork() {
        PushSender.Result result = senderWithBatchesOf(500).send(List.of(), MESSAGE);

        assertThat(result).isEqualTo(PushSender.Result.NOTHING);
        assertThat(received).isEmpty();
    }
}
