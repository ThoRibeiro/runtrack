package com.runtrack.engagement.event;

import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import java.time.Instant;

/**
 * Un « j'aime » retiré.
 *
 * <p>Le prompt ne le prévoyait pas. Sans lui, le compteur de la projection du fil ne redescend
 * jamais : un like annulé resterait affiché indéfiniment. Un compteur qui ne sait que monter n'est
 * pas un compteur.
 */
public record ActivityUnliked(
        ActivityId activityId, UserId ownerId, UserId likerId, Instant at, String correlationId) {
}
