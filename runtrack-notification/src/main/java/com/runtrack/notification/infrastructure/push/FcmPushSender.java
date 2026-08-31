package com.runtrack.notification.infrastructure.push;

import com.runtrack.notification.usecases.port.PushSender;
import com.runtrack.notification.usecases.model.push.DeviceToken;
import com.runtrack.notification.usecases.model.push.PushMessage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * L'envoi vers Firebase Cloud Messaging, en HTTP v1.
 *
 * <p><b>Un écart au §7, et il est subi.</b> Le §7 demande un multicast « jamais un appel par ami ».
 * L'endpoint {@code /batch} qui le permettait a été fermé par Google : l'API v1 n'accepte plus
 * qu'un message par requête, et le SDK officiel lui-même envoie désormais un appel par jeton.
 * Ce qui reste de la consigne est appliqué : les appareils sont découpés en lots de 500 au plus, et
 * les appels d'un lot partent <b>en parallèle sur des fils virtuels</b> — le coût est donc celui
 * d'un aller-retour par lot, pas d'un aller-retour par ami en série.
 *
 * <p>Les jetons que Firebase déclare inconnus ou révoqués remontent dans le résultat, et
 * {@code PushDelivery} les efface. Sans cela, chaque fan-out retenterait indéfiniment des
 * téléphones désinstallés depuis des mois.
 */
@Component
@ConditionalOnProperty(name = "runtrack.push.provider", havingValue = "fcm")
class FcmPushSender implements PushSender {

    private static final Logger LOG = LoggerFactory.getLogger(FcmPushSender.class);

    /** Ce que Firebase répond pour un jeton qui n'existe plus : le seul cas où l'on purge. */
    private static final String UNREGISTERED = "UNREGISTERED";
    private static final String INVALID_ARGUMENT = "INVALID_ARGUMENT";

    private final RestClient http;
    private final FcmAccessTokens tokens;
    private final PushProperties properties;

    FcmPushSender(RestClient.Builder builder, FcmAccessTokens tokens, PushProperties properties) {
        this.http = builder.baseUrl(properties.baseUrl()).build();
        this.tokens = tokens;
        this.properties = properties;
    }

    @Override
    public Result send(List<DeviceToken> devices, PushMessage message) {
        if (devices.isEmpty()) {
            return Result.NOTHING;
        }
        String bearer = tokens.current();
        var invalid = new HashSet<String>();
        int delivered = 0;

        for (List<DeviceToken> batch : batchesOf(devices)) {
            Outcome outcome = sendBatch(batch, message, bearer);
            delivered += outcome.delivered();
            invalid.addAll(outcome.invalid());
        }
        return new Result(delivered, invalid);
    }

    private Outcome sendBatch(List<DeviceToken> batch, PushMessage message, String bearer) {
        // Un fil virtuel par appel : ils passent leur vie à attendre le réseau, c'est exactement
        // ce pour quoi ils existent. Le lot borne la concurrence sans qu'on ait à la régler.
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> results = batch.stream()
                    .map(device -> pool.submit(() -> sendOne(device, message, bearer)))
                    .toList();

            int delivered = 0;
            var invalid = new ArrayList<String>();
            for (int index = 0; index < results.size(); index++) {
                String rejected = await(results.get(index));
                if (rejected == null) {
                    delivered++;
                } else if (!rejected.isEmpty()) {
                    invalid.add(batch.get(index).token());
                }
            }
            return new Outcome(delivered, invalid);
        }
    }

    /**
     * @return {@code null} si l'appareil a reçu, une chaîne vide en cas d'échec passager, ou le
     *     code d'erreur quand Firebase déclare le jeton mort
     */
    private String sendOne(DeviceToken device, PushMessage message, String bearer) {
        try {
            http.post()
                    .uri("/v1/projects/{project}/messages:send", properties.projectId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(bodyFor(device, message))
                    .retrieve()
                    .toBodilessEntity();
            return null;
        } catch (org.springframework.web.client.RestClientResponseException refused) {
            String reason = reasonOf(refused);
            if (isDead(refused.getStatusCode(), reason)) {
                return reason;
            }
            // Quota dépassé, panne passagère : on ne purge rien. Effacer un jeton sur une erreur
            // temporaire couperait définitivement un appareil parfaitement joignable.
            LOG.warn("Push refusé par Firebase ({}) : {}", refused.getStatusCode(), reason);
            return "";
        } catch (RuntimeException unreachable) {
            LOG.warn("Firebase injoignable : {}", unreachable.getMessage());
            return "";
        }
    }

    private static boolean isDead(HttpStatusCode status, String reason) {
        return status.value() == 404 || UNREGISTERED.equals(reason)
                || (status.value() == 400 && INVALID_ARGUMENT.equals(reason));
    }

    private static String reasonOf(org.springframework.web.client.RestClientResponseException refused) {
        String body = refused.getResponseBodyAsString();
        // Lecture volontairement grossière : on ne cherche qu'un code d'erreur dans un corps dont
        // la forme exacte n'est pas contractuelle, et se tromper coûte un jeton gardé de trop.
        if (body.contains(UNREGISTERED)) {
            return UNREGISTERED;
        }
        return body.contains(INVALID_ARGUMENT) ? INVALID_ARGUMENT : "";
    }

    /**
     * La charge utile FCM.
     *
     * <p>Le lien profond voyage en {@code data} et non dans la notification : c'est ce que
     * l'application lit à l'ouverture pour aller droit au suivi live (§7).
     */
    private static Map<String, Object> bodyFor(DeviceToken device, PushMessage message) {
        return Map.of("message", Map.of(
                "token", device.token(),
                "notification", Map.of("title", message.title(), "body", message.body()),
                "data", Map.of("deepLink", message.deepLink())));
    }

    private List<List<DeviceToken>> batchesOf(List<DeviceToken> devices) {
        var batches = new ArrayList<List<DeviceToken>>();
        for (int start = 0; start < devices.size(); start += properties.batchSize()) {
            batches.add(devices.subList(start, Math.min(start + properties.batchSize(), devices.size())));
        }
        return batches;
    }

    private static String await(Future<String> result) {
        try {
            return result.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return "";
        } catch (java.util.concurrent.ExecutionException failed) {
            LOG.warn("Envoi push interrompu : {}", failed.getCause().getMessage());
            return "";
        }
    }

    private record Outcome(int delivered, List<String> invalid) {
    }
}
