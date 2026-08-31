package com.runtrack.engagement.event;

import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import java.time.Instant;

/**
 * Quelqu'un a aimé une course.
 *
 * <p>Porte le propriétaire de la course en plus de son identifiant : c'est lui le destinataire de
 * la notification, et le résoudre ici évite à {@code notification} et à {@code feed} de rappeler
 * {@code course} une fois par événement.
 *
 * <p>{@code likeCount} est le total <em>après</em> ce « j'aime ». Il porte l'agrégation du §7 —
 * « Marie et 4 autres ont aimé » : sans lui, {@code notification} devrait interroger
 * {@code engagement}, alors qu'il n'en connaît que les événements.
 */
public record ActivityLiked(
        ActivityId activityId, UserId ownerId, UserId likerId, long likeCount,
        Instant at, String correlationId) {
}
