package com.contec.pms.web.dto.request;

import com.contec.pms.domain.enums.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateTaskRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String description,
        @NotNull TaskPriority priority,
        LocalDate expectedCompletionDate,
        @NotNull(message = "version is required so concurrent edits are not overwritten") Long version) {
}
