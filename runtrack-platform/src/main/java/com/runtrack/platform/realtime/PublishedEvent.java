package com.runtrack.platform.realtime;

import java.util.Map;

/**
 * Un événement prêt à partir en SSE, et à écrire tel quel dans un Stream.
 *
 * <p>Une seule sérialisation pour deux transports : ce qui est écrit dans Dragonfly est
 * exactement ce que le client reçoit. L'instance qui relaie n'a donc rien à désérialiser — elle
 * recopie la charge utile — et il n'existe aucun moyen que les deux représentations divergent.
 *
 * <p>{@code eventId} est l'identifiant d'entrée du Stream, et devient le champ {@code id:} du
 * message SSE : c'est lui que le client renvoie en {@code Last-Event-ID} pour reprendre où il
 * s'était arrêté. Nul pour un événement d'instantané, qui n'est pas une entrée du journal —
 * laisser un client reprendre depuis un identifiant inventé rouvrirait le trou que toute cette
 * mécanique existe pour fermer.
 */
public record PublishedEvent(String eventId, String kind, String payload) {

    static final String KIND_FIELD = "kind";
    static final String PAYLOAD_FIELD = "payload";

    public PublishedEvent {
        if (kind == null || payload == null) {
            throw new IllegalArgumentException("Événement incomplet");
        }
    }

    public static PublishedEvent withoutId(String kind, String payload) {
        return new PublishedEvent(null, kind, payload);
    }

    Map<String, String> asEntry() {
        return Map.of(KIND_FIELD, kind, PAYLOAD_FIELD, payload);
    }

    /**
     * Une entrée de stream relue.
     *
     * <p>Une entrée à qui il manque un champ rend {@code null} plutôt que de lever : elle vient
     * d'une version antérieure du format, et faire tomber la diffusion de tout un sujet pour une
     * entrée illisible serait disproportionné.
     */
    static PublishedEvent fromEntry(String eventId, Map<?, ?> entry) {
        Object kind = entry.get(KIND_FIELD);
        Object payload = entry.get(PAYLOAD_FIELD);
        if (kind == null || payload == null) {
            return null;
        }
        return new PublishedEvent(eventId, kind.toString(), payload.toString());
    }
}
