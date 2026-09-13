package com.contec.pms.web.dto.request;

import com.contec.pms.domain.enums.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateTaskRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String description,
        TaskPriority priority,
        LocalDate expectedCompletionDate,
        /** Optional: assign at creation time. Must be a SITE_ENGINEER on this project. */
        Long assigneeId) {
}
