package com.runtrack.course.internal.domain.access;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ViewerRelationTest {

    @Test
    void namesTheRelationsThatMatter() {
        assertThat(ViewerRelation.owner().isOwner()).isTrue();
        assertThat(ViewerRelation.acceptedFollower().isAcceptedFollower()).isTrue();
        assertThat(ViewerRelation.blocked().isBlockedEitherWay()).isTrue();
    }

    @Test
    void aStrangerHasNoRelationAtAll() {
        assertThat(ViewerRelation.NONE.isOwner()).isFalse();
        assertThat(ViewerRelation.NONE.isAcceptedFollower()).isFalse();
        assertThat(ViewerRelation.NONE.isBlockedEitherWay()).isFalse();
    }
}
