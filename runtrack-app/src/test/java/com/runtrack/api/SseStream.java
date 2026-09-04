package com.runtrack.api;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Predicate;

/**
 * Un flux SSE lu sur une vraie connexion HTTP.
 *
 * <p><b>Pourquoi pas MockMvc.</b> Le pompage d'un abonné démarre pendant que la requête remonte
 * encore la chaîne de filtres — c'est voulu, l'instantané doit partir sans attendre. Deux fils
 * écrivent donc dans la même réponse : la pompe, et la chaîne de sécurité qui pose son en-tête
 * {@code Content-Security-Policy} en sortant. Une réponse de conteneur le supporte, c'est le
 * fonctionnement normal du SSE ; {@code MockHttpServletResponse} non — ses en-têtes vivent dans
 * une table non synchronisée, et la requête meurt alors en
 * {@link java.util.ConcurrentModificationException}. Une fois sur quelques dizaines, sur une
 * machine chargée : assez rare pour passer en local, assez fréquent pour rougir en intégration.
 *
 * <p>Le corps est lu par un fil dédié, au fur et à mesure : un flux qui ne se referme jamais ne
 * peut pas être récupéré d'un bloc à la fin.
 */
final class SseStream implements AutoCloseable {

    /**
     * De quoi laisser passer un aller-retour par Dragonfly et son {@code XREAD}, jamais de quoi
     * cacher une panne : au-delà, ce n'est plus de la latence.
     */
    private static final Duration PATIENCE = Duration.ofSeconds(10);

    private static final Duration POLL = Duration.ofMillis(50);

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** Synchronisé : le fil de lecture écrit pendant que le test relit. */
    private final StringBuffer received = new StringBuffer();

    private final InputStream body;
    private volatile boolean ended;

    private SseStream(InputStream body) {
        this.body = body;
        Thread.ofVirtual().name("sse-test-reader").start(this::drain);
    }

    /**
     * Ouvre le flux et rend la main dès les en-têtes reçus.
     *
     * @param bearer l'en-tête d'autorisation, ou {@code null} pour un spectateur anonyme
     * @param lastEventId la reprise demandée, ou {@code null} pour un premier branchement
     */
    static SseStream open(int port, String path, String bearer, String lastEventId)
            throws IOException, InterruptedException {

        HttpRequest.Builder request = HttpRequest
                .newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "text/event-stream")
                .timeout(PATIENCE)
                .GET();
        if (bearer != null) {
            request.header("Authorization", bearer);
        }
        if (lastEventId != null) {
            request.header("Last-Event-ID", lastEventId);
        }

        HttpResponse<InputStream> response =
                CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new AssertionError("Flux refusé : HTTP " + response.statusCode());
        }
        return new SseStream(response.body());
    }

    /**
     * Attend que le flux contienne ce qu'on cherche.
     *
     * <p>Une attente fixe serait soit trop courte — le relais passe par Dragonfly — soit
     * inutilement lente.
     */
    String await(Predicate<String> until) throws InterruptedException {
        Instant deadline = Instant.now().plus(PATIENCE);
        while (Instant.now().isBefore(deadline)) {
            String content = content();
            if (until.test(content)) {
                return content;
            }
            Thread.sleep(POLL);
        }
        throw new AssertionError("Flux incomplet après " + PATIENCE + " :\n" + content());
    }

    /** Attend que le serveur raccroche, et rend ce qui a été reçu en tout. */
    String awaitEnd() throws InterruptedException {
        Instant deadline = Instant.now().plus(PATIENCE);
        while (Instant.now().isBefore(deadline)) {
            if (ended) {
                return content();
            }
            Thread.sleep(POLL);
        }
        throw new AssertionError("Le flux ne s'est jamais refermé :\n" + content());
    }

    String content() {
        return received.toString();
    }

    /** Referme la connexion : sans cela, chaque test laisserait un spectateur derrière lui. */
    @Override
    public void close() {
        try {
            body.close();
        } catch (IOException alreadyGone) {
            // Le serveur avait déjà raccroché : c'est le cas nominal d'une course terminée.
        }
    }

    private void drain() {
        try (BufferedReader lines =
                new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = lines.readLine()) != null) {
                received.append(line).append('\n');
            }
        } catch (IOException disconnected) {
            // Fermeture demandée par le test, ou serveur parti : ce qui a été lu reste lisible.
        } finally {
            ended = true;
        }
    }
}
