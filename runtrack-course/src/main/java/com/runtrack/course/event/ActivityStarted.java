package com.runtrack.course.event;

import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import java.time.Instant;

/**
 * Une course a démarré.
 *
 * <p>Porte la portée effective, déjà composée avec celle du compte : les destinataires du
 * fan-out sont filtrés dessus, et {@code notification} n'a pas à refaire ce calcul.
 */
public record ActivityStarted(
        ActivityId activityId,
        UserId ownerId,
        String activityType,
        String effectiveScope,
        Instant at,
        String correlationId) {
}
