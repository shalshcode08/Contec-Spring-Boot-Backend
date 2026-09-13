package com.contec.pms.service;

import com.contec.pms.domain.entity.Task;
import com.contec.pms.domain.entity.TaskActivity;
import com.contec.pms.domain.entity.User;
import com.contec.pms.domain.enums.ActivityType;
import com.contec.pms.domain.enums.TaskStatus;
import com.contec.pms.repository.TaskActivityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the task audit trail. Every {@code record} call runs inside the caller's
 * transaction (MANDATORY), so an activity row can never be committed without the
 * state change that produced it — and vice versa.
 */
@Service
public class TaskActivityService {

    private final TaskActivityRepository taskActivityRepository;

    public TaskActivityService(TaskActivityRepository taskActivityRepository) {
        this.taskActivityRepository = taskActivityRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public TaskActivity record(Task task, User actor, ActivityType type,
                               TaskStatus oldStatus, TaskStatus newStatus,
                               Integer oldProgress, Integer newProgress,
                               String detail) {
        TaskActivity activity = new TaskActivity(task, actor, type)
                .withStatusChange(oldStatus, newStatus)
                .withProgressChange(oldProgress, newProgress)
                .withDetail(detail);
        return taskActivityRepository.save(activity);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public TaskActivity record(Task task, User actor, ActivityType type, String detail) {
        return record(task, actor, type, null, null, null, null, detail);
    }
}
