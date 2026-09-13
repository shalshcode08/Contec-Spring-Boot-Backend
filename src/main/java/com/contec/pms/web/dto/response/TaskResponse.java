package com.contec.pms.web.dto.response;

import com.contec.pms.domain.entity.Task;
import com.contec.pms.domain.enums.TaskPriority;
import com.contec.pms.domain.enums.TaskStatus;

import java.time.Instant;
import java.time.LocalDate;

public record TaskResponse(
        Long id,
        Long projectId,
        String projectName,
        String title,
        String description,
        UserSummaryResponse assignee,
        TaskStatus status,
        TaskPriority priority,
        int progress,
        LocalDate expectedCompletionDate,
        Instant completedAt,
        UserSummaryResponse approvedBy,
        Instant approvedAt,
        UserSummaryResponse rejectedBy,
        Instant rejectedAt,
        String rejectionReason,
        UserSummaryResponse createdBy,
        Instant createdAt,
        Instant updatedAt,
        Long version) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getProject().getId(),
                task.getProject().getName(),
                task.getTitle(),
                task.getDescription(),
                UserSummaryResponse.from(task.getAssignee()),
                task.getStatus(),
                task.getPriority(),
                task.getProgress(),
                task.getExpectedCompletionDate(),
                task.getCompletedAt(),
                UserSummaryResponse.from(task.getApprovedBy()),
                task.getApprovedAt(),
                UserSummaryResponse.from(task.getRejectedBy()),
                task.getRejectedAt(),
                task.getRejectionReason(),
                UserSummaryResponse.from(task.getCreatedBy()),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getVersion());
    }
}
