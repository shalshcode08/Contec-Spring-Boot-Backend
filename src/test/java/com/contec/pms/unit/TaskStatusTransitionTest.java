package com.contec.pms.unit;

import com.contec.pms.domain.enums.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TaskStatusTransitionTest {

    @ParameterizedTest
    @CsvSource({
            "TODO, IN_PROGRESS",
            "IN_PROGRESS, COMPLETED",
            "COMPLETED, APPROVED",
            "COMPLETED, REJECTED",
            "REJECTED, IN_PROGRESS"
    })
    @DisplayName("valid transitions are allowed")
    void allowsValidTransitions(TaskStatus from, TaskStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "TODO, COMPLETED",
            "TODO, APPROVED",
            "TODO, REJECTED",
            "IN_PROGRESS, APPROVED",
            "IN_PROGRESS, REJECTED",
            "IN_PROGRESS, TODO",
            "COMPLETED, IN_PROGRESS",
            "COMPLETED, TODO",
            "APPROVED, IN_PROGRESS",
            "APPROVED, REJECTED",
            "REJECTED, APPROVED",
            "REJECTED, COMPLETED"
    })
    @DisplayName("invalid transitions are rejected")
    void rejectsInvalidTransitions(TaskStatus from, TaskStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @Test
    void approvedIsTerminal() {
        assertThat(TaskStatus.APPROVED.allowedTransitions()).isEmpty();
        assertThat(TaskStatus.APPROVED.isTerminal()).isTrue();
    }

    @Test
    void progressIsEditableOnlyBeforeCompletion() {
        assertThat(TaskStatus.TODO.allowsProgressUpdate()).isTrue();
        assertThat(TaskStatus.IN_PROGRESS.allowsProgressUpdate()).isTrue();
        assertThat(TaskStatus.REJECTED.allowsProgressUpdate()).isTrue();
        assertThat(TaskStatus.COMPLETED.allowsProgressUpdate()).isFalse();
        assertThat(TaskStatus.APPROVED.allowsProgressUpdate()).isFalse();
    }

    @Test
    void nullTargetIsNeverAllowed() {
        assertThat(TaskStatus.TODO.canTransitionTo(null)).isFalse();
    }
}
