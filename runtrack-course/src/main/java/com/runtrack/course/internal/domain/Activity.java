package com.runtrack.course.internal.domain;

import com.runtrack.shared.ActivityId;
import com.runtrack.shared.AudienceScope;
import com.runtrack.shared.UserId;
import com.runtrack.shared.error.ConflictException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * L'agrégat racine : une course, son cycle de vie et ses invariants.
 *
 * <p>Les transitions passent toutes par ici. Une transition illégale — reprendre une
 * course déjà terminée, mettre en pause ce qui l'est déjà — lève une
 * {@link ConflictException} portant un code stable, jamais un booléen que l'appelant
 * pourrait oublier de tester.
 */
public final class Activity {

    private static final int MAX_TITLE_LENGTH = 120;
    private static final int MAX_DESCRIPTION_LENGTH = 2_000;

    private final ActivityId id;
    private final UserId ownerId;
    private final ActivityType type;
    private final Instant startedAt;
    private final DeviceClockSkew clockSkew;

    private String title;
    private String description;
    private AudienceScope scope;
    private ActivityStatus status;

    private Activity(ActivityId id, UserId ownerId, ActivityType type, String title,
            String description, AudienceScope scope, Instant startedAt, DeviceClockSkew clockSkew) {
        this.id = id;
        this.ownerId = ownerId;
        this.type = type;
        this.startedAt = startedAt;
        this.clockSkew = clockSkew;
        this.scope = scope;
        this.status = new ActivityStatus.Live(startedAt);
        rename(title, description);
    }

    public static Activity start(ActivityId id, UserId ownerId, ActivityType type, String title,
            String description, AudienceScope scope, Instant startedAt, DeviceClockSkew clockSkew) {
        if (id == null || ownerId == null || type == null || scope == null
                || startedAt == null || clockSkew == null) {
            throw new IllegalArgumentException("Démarrage de course incomplet");
        }
        return new Activity(id, ownerId, type, title, description, scope, startedAt, clockSkew);
    }

    public void pause(Instant at) {
        requireLive("ACTIVITY_NOT_LIVE", "Seule une course en cours peut être mise en pause");
        status = new ActivityStatus.Paused(at);
    }

    public void resume(Instant at) {
        if (!(status instanceof ActivityStatus.Paused)) {
            throw new ConflictException("ACTIVITY_NOT_PAUSED", "Seule une course en pause peut reprendre");
        }
        status = new ActivityStatus.Live(at);
    }

    public void finish(Instant at) {
        requireNotTerminal("ACTIVITY_ALREADY_ENDED", "Cette course est déjà terminée ou abandonnée");
        status = new ActivityStatus.Finished(at);
    }

    public void discard(Instant at) {
        requireNotTerminal("ACTIVITY_ALREADY_ENDED", "Cette course est déjà terminée ou abandonnée");
        status = new ActivityStatus.Discarded(at);
    }

    public void rename(String newTitle, String newDescription) {
        if (newTitle == null || newTitle.isBlank()) {
            throw new IllegalArgumentException("Une course a besoin d'un titre");
        }
        if (newTitle.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("Titre trop long : " + newTitle.length() + " caractères");
        }
        if (newDescription != null && newDescription.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("Description trop longue : " + newDescription.length());
        }
        this.title = newTitle.strip();
        this.description = newDescription == null ? null : newDescription.strip();
    }

    public void changeScope(AudienceScope newScope) {
        if (newScope == null) {
            throw new IllegalArgumentException("Portée de visibilité absente");
        }
        this.scope = newScope;
    }

    /** N'accepte des points que tant qu'elle est en cours : ni en pause, ni terminée. */
    public void requireAcceptingPoints() {
        if (!status.acceptsPoints()) {
            throw new ConflictException("ACTIVITY_NOT_ACCEPTING_POINTS",
                    "Cette course n'enregistre pas de points dans son état actuel");
        }
    }

    public Duration elapsedAt(Instant now) {
        Instant end = status.isTerminal() ? status.since() : now;
        Duration elapsed = Duration.between(startedAt, end);
        return elapsed.isNegative() ? Duration.ZERO : elapsed;
    }

    public ActivityAudience audienceWith(AudienceScope ownerAccountScope) {
        return new ActivityAudience(id, ownerId, scope, ownerAccountScope);
    }

    private void requireLive(String code, String message) {
        if (!(status instanceof ActivityStatus.Live)) {
            throw new ConflictException(code, message);
        }
    }

    private void requireNotTerminal(String code, String message) {
        if (status.isTerminal()) {
            throw new ConflictException(code, message);
        }
    }

    public ActivityId id() {
        return id;
    }

    public UserId ownerId() {
        return ownerId;
    }

    public ActivityType type() {
        return type;
    }

    public String title() {
        return title;
    }

    public Optional<String> description() {
        return Optional.ofNullable(description);
    }

    public AudienceScope scope() {
        return scope;
    }

    public ActivityStatus status() {
        return status;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public DeviceClockSkew clockSkew() {
        return clockSkew;
    }
}
