package com.runtrack.social.internal.infra.rest.dto;

import java.time.Instant;
import java.util.List;

/** Les contrats HTTP de {@code social}. */
public final class SocialDtos {

    private SocialDtos() {
    }

    /** Le résultat d'un abonnement : le client doit savoir s'il attend une acceptation. */
    public record FollowResponse(String status, boolean pending) {
    }

    public record PendingRequest(String requestId, String followerId, Instant requestedAt) {
    }

    public record UserIdList(List<String> userIds, int count) {

        public static UserIdList of(java.util.Collection<com.runtrack.shared.id.UserId> ids) {
            List<String> values = ids.stream().map(Object::toString).toList();
            return new UserIdList(values, values.size());
        }
    }
}
