package com.runtrack.platform.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StreamEntryIdTest {

    /** Le piège que la classe existe pour éviter : en tri lexical, « 9 » passerait après « 10 ». */
    @Test
    void comparesTheTimestampAsANumberNotAsText() {
        assertThat(StreamEntryId.compare("9-0", "10-0")).isNegative();
        assertThat(StreamEntryId.compare("10-0", "9-0")).isPositive();
    }

    @Test
    void separatesTwoEntriesOfTheSameMillisecondByTheirSequence() {
        assertThat(StreamEntryId.compare("1700000000000-1", "1700000000000-2")).isNegative();
        assertThat(StreamEntryId.compare("1700000000000-2", "1700000000000-2")).isZero();
    }

    /** Redis accepte un identifiant sans séquence : elle vaut alors zéro. */
    @Test
    void anIdWithoutASequenceCountsAsZero() {
        assertThat(StreamEntryId.compare("1700000000000", "1700000000000-0")).isZero();
        assertThat(StreamEntryId.compare("1700000000000", "1700000000000-1")).isNegative();
    }
}
