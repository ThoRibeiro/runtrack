package com.runtrack.social.internal.domain.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.error.ConflictException;
import com.runtrack.shared.id.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FollowTest {

    private static final UserId MARIE = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000001"));
    private static final UserId PAUL = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000002"));
    private static final Instant NOW = Instant.parse("2026-08-29T08:00:00Z");

    /** C'est la portée du compte suivi qui décide, pas l'appelant. */
    @ParameterizedTest
    @CsvSource({"PUBLIC, ACCEPTED", "FOLLOWERS, PENDING", "PRIVATE, PENDING"})
    void theFolloweeAccountDecidesTheInitialState(AudienceScope scope, FollowStatus expected) {
        Follow follow = Follow.request(UUID.randomUUID(), MARIE, PAUL, scope, NOW);

        assertThat(follow.status()).isEqualTo(expected);
        assertThat(follow.isAccepted()).isEqualTo(expected.isAccepted());
    }

    @Test
    void anAutomaticFollowIsAcceptedAtOnce() {
        Follow follow = Follow.request(UUID.randomUUID(), MARIE, PAUL, AudienceScope.PUBLIC, NOW);

        assertThat(follow.acceptedAt()).contains(NOW);
        assertThat(follow.requestedAt()).isEqualTo(NOW);
        assertThat(follow.followerId()).isEqualTo(MARIE);
        assertThat(follow.followeeId()).isEqualTo(PAUL);
        assertThat(follow.id()).isNotNull();
    }

    @Test
    void aPendingRequestHasNoAcceptanceDate() {
        Follow follow = Follow.request(UUID.randomUUID(), MARIE, PAUL, AudienceScope.PRIVATE, NOW);

        assertThat(follow.acceptedAt()).isEmpty();

        follow.accept(NOW.plusSeconds(60));

        assertThat(follow.isAccepted()).isTrue();
        assertThat(follow.acceptedAt()).contains(NOW.plusSeconds(60));
    }

    @Test
    void acceptingTwiceIsAConflict() {
        Follow follow = Follow.request(UUID.randomUUID(), MARIE, PAUL, AudienceScope.PUBLIC, NOW);

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> follow.accept(NOW))
                .extracting(ConflictException::code)
                .isEqualTo("FOLLOW_ALREADY_ACCEPTED");
    }

    @Test
    void nobodyFollowsThemselves() {
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> Follow.request(UUID.randomUUID(), MARIE, MARIE, AudienceScope.PUBLIC, NOW))
                .extracting(ConflictException::code)
                .isEqualTo("SELF_FOLLOW");
    }

    /** Seul le compte suivi dispose de sa propre liste d'abonnés. */
    @Test
    void onlyTheFolloweeMayActOnTheRequest() {
        Follow follow = Follow.request(UUID.randomUUID(), MARIE, PAUL, AudienceScope.PRIVATE, NOW);

        follow.requireOwnedBy(PAUL);

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> follow.requireOwnedBy(MARIE))
                .extracting(ConflictException::code)
                .isEqualTo("NOT_YOUR_REQUEST");
    }

    @Test
    void refusesToBeBuiltIncomplete() {
        assertThatIllegalArgumentException().isThrownBy(() -> Follow.request(
                null, MARIE, PAUL, AudienceScope.PUBLIC, NOW));
        assertThatIllegalArgumentException().isThrownBy(() -> Follow.request(
                UUID.randomUUID(), null, PAUL, AudienceScope.PUBLIC, NOW));
        assertThatIllegalArgumentException().isThrownBy(() -> Follow.request(
                UUID.randomUUID(), MARIE, null, AudienceScope.PUBLIC, NOW));
        assertThatIllegalArgumentException().isThrownBy(() -> Follow.request(
                UUID.randomUUID(), MARIE, PAUL, AudienceScope.PUBLIC, null));
    }

    @Test
    void rehydratesAPersistedState() {
        Follow follow = Follow.rehydrate(UUID.randomUUID(), MARIE, PAUL,
                FollowStatus.ACCEPTED, NOW, NOW.plusSeconds(30));

        assertThat(follow.isAccepted()).isTrue();
        assertThat(follow.acceptedAt()).contains(NOW.plusSeconds(30));
    }
}
