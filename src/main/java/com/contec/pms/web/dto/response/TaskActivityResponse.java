package com.contec.pms.web.dto.response;

import com.contec.pms.domain.entity.TaskActivity;
import com.contec.pms.domain.enums.ActivityType;
import com.contec.pms.domain.enums.TaskStatus;

import java.time.Instant;

/** What happened, who did it, and when. */
public record TaskActivityResponse(
        Long id,
        Long taskId,
        ActivityType activityType,
        UserSummaryResponse actor,
        TaskStatus oldStatus,
        TaskStatus newStatus,
        Integer oldProgress,
        Integer newProgress,
        String detail,
        Instant createdAt) {

    public static TaskActivityResponse from(TaskActivity activity) {
        return new TaskActivityResponse(
                activity.getId(),
                activity.getTask().getId(),
                activity.getActivityType(),
                UserSummaryResponse.from(activity.getActor()),
                activity.getOldStatus(),
                activity.getNewStatus(),
                activity.getOldProgress(),
                activity.getNewProgress(),
                activity.getDetail(),
                activity.getCreatedAt());
    }
}
