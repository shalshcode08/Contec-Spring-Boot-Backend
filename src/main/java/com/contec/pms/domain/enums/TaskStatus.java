package com.contec.pms.domain.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Task lifecycle and the single source of truth for which transitions are legal.
 *
 * <pre>
 *   TODO ──▶ IN_PROGRESS ──▶ COMPLETED ──▶ APPROVED (terminal)
 *                 ▲                └────▶ REJECTED
 *                 └───────────────────────────┘   (rework)
 * </pre>
 */
public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    COMPLETED,
    APPROVED,
    REJECTED;

    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED = Map.of(
            TODO, EnumSet.of(IN_PROGRESS),
            IN_PROGRESS, EnumSet.of(COMPLETED),
            COMPLETED, EnumSet.of(APPROVED, REJECTED),
            REJECTED, EnumSet.of(IN_PROGRESS),
            APPROVED, Collections.emptySet());

    public boolean canTransitionTo(TaskStatus target) {
        return target != null && ALLOWED.get(this).contains(target);
    }

    public Set<TaskStatus> allowedTransitions() {
        return Collections.unmodifiableSet(ALLOWED.get(this));
    }

    /** Statuses in which an engineer may still move the progress figure. */
    public boolean allowsProgressUpdate() {
        return this == TODO || this == IN_PROGRESS || this == REJECTED;
    }

    public boolean isTerminal() {
        return this == APPROVED;
    }
}
