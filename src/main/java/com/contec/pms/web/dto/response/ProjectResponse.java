package com.contec.pms.web.dto.response;

import com.contec.pms.domain.entity.Project;
import com.contec.pms.domain.enums.ProjectStatus;

import java.time.Instant;
import java.time.LocalDate;

public record ProjectResponse(
        Long id,
        String name,
        String description,
        String location,
        LocalDate startDate,
        LocalDate expectedCompletionDate,
        ProjectStatus status,
        UserSummaryResponse createdBy,
        Instant createdAt,
        Instant updatedAt,
        Long version) {

    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getLocation(),
                project.getStartDate(),
                project.getExpectedCompletionDate(),
                project.getStatus(),
                UserSummaryResponse.from(project.getCreatedBy()),
                project.getCreatedAt(),
                project.getUpdatedAt(),
                project.getVersion());
    }
}
