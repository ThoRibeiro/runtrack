package com.runtrack.shared.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class DomainExceptionTest {

    @Test
    void carriesAStableBusinessCodeAlongsideTheMessage() {
        var exception = new NotFoundException("ACTIVITY_NOT_FOUND", "Course introuvable");

        assertThat(exception.code()).isEqualTo("ACTIVITY_NOT_FOUND");
        assertThat(exception).hasMessage("Course introuvable");
    }

    @Test
    void everySubtypeCarriesItsCode() {
        assertThat(new ConflictException("ALREADY_FINISHED", "…").code()).isEqualTo("ALREADY_FINISHED");
        assertThat(new ForbiddenException("NOT_VISIBLE", "…").code()).isEqualTo("NOT_VISIBLE");
    }

    /** Sans code, le client n'a que le message pour réagir — et le message change. */
    @Test
    void refusesToBeBuiltWithoutACode() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new NotFoundException(null, "…"))
                .withMessageContaining("sans code");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new NotFoundException("   ", "…"));
    }
}
