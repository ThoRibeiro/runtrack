package com.runtrack.course.internal.infra.realtime;

/**
 * Un événement prêt à partir en SSE.
 *
 * <p>{@code eventId} est l'identifiant d'entrée du Stream Dragonfly, et devient le champ
 * {@code id:} du message SSE — c'est lui que le client renvoie en {@code Last-Event-ID} pour
 * reprendre où il s'était arrêté. Nul pour un événement d'instantané, qui n'est pas une entrée
 * du journal : laisser un client reprendre depuis un identifiant inventé rouvrirait le trou
 * que toute cette mécanique existe pour fermer.
 */
public record RecordedEvent(String eventId, String kind, String payload) {

    public RecordedEvent {
        if (kind == null || payload == null) {
            throw new IllegalArgumentException("Événement incomplet");
        }
    }

    public static RecordedEvent withoutId(String kind, String payload) {
        return new RecordedEvent(null, kind, payload);
    }
}
