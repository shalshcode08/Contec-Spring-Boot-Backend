package com.contec.pms.web.dto.request;

import com.contec.pms.domain.enums.ProjectStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateProjectRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 5000) String description,
        @Size(max = 255) String location,
        @NotNull LocalDate startDate,
        @NotNull LocalDate expectedCompletionDate,
        ProjectStatus status,
        Long managerId) {
}
