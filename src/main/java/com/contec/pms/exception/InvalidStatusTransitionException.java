package com.contec.pms.exception;

import com.contec.pms.domain.enums.TaskStatus;
import org.springframework.http.HttpStatus;

public class InvalidStatusTransitionException extends ApiException {

    public InvalidStatusTransitionException(TaskStatus from, TaskStatus to) {
        super(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION",
                "Cannot move a task from " + from + " to " + to
                        + ". Allowed from " + from + ": " + from.allowedTransitions());
    }

    public InvalidStatusTransitionException(String message) {
        super(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION", message);
    }
}
