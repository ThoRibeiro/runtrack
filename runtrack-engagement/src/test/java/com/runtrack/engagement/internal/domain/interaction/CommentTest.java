package com.runtrack.engagement.internal.domain.interaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.runtrack.shared.error.ConflictException;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CommentTest {

    private static final ActivityId RUN = new ActivityId(UUID.fromString("018f4c1e-0000-7000-8000-0000000000ff"));
    private static final UserId MARIE = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000001"));
    private static final Instant NOON = Instant.parse("2026-08-31T12:00:00Z");

    private static Comment written(String body) {
        return Comment.written(new CommentId(UUID.randomUUID()), RUN, MARIE, Optional.empty(),
                body, NOON);
    }

    @Test
    void aCommentIsTrimmedAndKept() {
        assertThat(written("  Bravo !  ").body()).isEqualTo("Bravo !");
    }

    @Test
    void anEmptyCommentSaysNothingAndIsRefused() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> written("   "));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> written(null));
    }

    @Test
    void aCommentLongerThanTheColumnIsRefusedBeforeTheDatabaseSeesIt() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> written("a".repeat(Comment.MAX_LENGTH + 1)))
                .withMessageContaining(String.valueOf(Comment.MAX_LENGTH));
    }

    @Test
    void anEditWithinTheWindowIsAccepted() {
        Comment edited = written("Bravo").editedTo("Bravo !", NOON.plusSeconds(60));

        assertThat(edited.body()).isEqualTo("Bravo !");
        assertThat(edited.editedAt()).contains(NOON.plusSeconds(60));
    }

    /**
     * Passé la fenêtre, d'autres ont répondu : réécrire ce à quoi ils répondaient viderait
     * l'échange de son sens.
     */
    @Test
    void anEditAfterTheWindowIsRefused() {
        Comment comment = written("Bravo");

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> comment.editedTo("Autre chose", NOON.plus(Comment.EDIT_WINDOW)))
                .satisfies(refused -> assertThat(refused.code())
                        .isEqualTo("COMMENT_EDIT_WINDOW_CLOSED"));
    }

    /** La fenêtre court depuis l'écriture : une suite de corrections ne la fait pas glisser. */
    @Test
    void theWindowDoesNotSlideWithEachCorrection() {
        Comment edited = written("Bravo").editedTo("Bravo !", NOON.plus(Duration.ofMinutes(29)));

        assertThat(edited.isEditableAt(NOON.plus(Duration.ofMinutes(31)))).isFalse();
    }

    @Test
    void aDeletedCommentIsNoLongerEdited() {
        Comment deleted = written("Bravo").deletedAt(NOON.plusSeconds(10));

        assertThat(deleted.isDeleted()).isTrue();
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> deleted.editedTo("Autre", NOON.plusSeconds(20)))
                .satisfies(refused -> assertThat(refused.code()).isEqualTo("COMMENT_DELETED"));
    }

    @Test
    void deletingTwiceKeepsTheFirstMoment() {
        Comment deleted = written("Bravo").deletedAt(NOON);

        assertThat(deleted.deletedAt(NOON.plusSeconds(60)).deletedAt()).contains(NOON);
    }

    @Test
    void aReplyKnowsThatItIsOne() {
        Comment reply = Comment.written(new CommentId(UUID.randomUUID()), RUN, MARIE,
                Optional.of(new CommentId(UUID.randomUUID())), "Merci", NOON);

        assertThat(reply.isReply()).isTrue();
        assertThat(written("Bravo").isReply()).isFalse();
    }
}
