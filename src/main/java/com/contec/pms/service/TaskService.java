package com.contec.pms.service;

import com.contec.pms.domain.entity.Project;
import com.contec.pms.domain.entity.Task;
import com.contec.pms.domain.entity.User;
import com.contec.pms.domain.enums.ActivityType;
import com.contec.pms.domain.enums.RoleName;
import com.contec.pms.domain.enums.TaskPriority;
import com.contec.pms.domain.enums.TaskStatus;
import com.contec.pms.exception.BusinessRuleException;
import com.contec.pms.exception.ForbiddenOperationException;
import com.contec.pms.exception.InvalidStatusTransitionException;
import com.contec.pms.exception.ResourceNotFoundException;
import com.contec.pms.exception.StaleResourceException;
import com.contec.pms.repository.TaskActivityRepository;
import com.contec.pms.repository.TaskRepository;
import com.contec.pms.repository.TaskSpecifications;
import com.contec.pms.repository.UserRepository;
import com.contec.pms.security.AppUserDetails;
import com.contec.pms.web.dto.request.ApproveTaskRequest;
import com.contec.pms.web.dto.request.AssignTaskRequest;
import com.contec.pms.web.dto.request.CompleteTaskRequest;
import com.contec.pms.web.dto.request.CreateTaskRequest;
import com.contec.pms.web.dto.request.RejectTaskRequest;
import com.contec.pms.web.dto.request.UpdateProgressRequest;
import com.contec.pms.web.dto.request.UpdateTaskRequest;
import com.contec.pms.web.dto.response.PagedResponse;
import com.contec.pms.web.dto.response.TaskActivityResponse;
import com.contec.pms.web.dto.response.TaskResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

/**
 * Task lifecycle. Every mutating method is transactional so that the task change
 * and its activity record commit together, and every one of them checks both the
 * caller's authority and the submitted optimistic-lock version.
 */
@Service
@Transactional(readOnly = true)
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskActivityRepository taskActivityRepository;
    private final UserRepository userRepository;
    private final AccessControlService accessControl;
    private final TaskActivityService activityService;

    public TaskService(TaskRepository taskRepository,
                       TaskActivityRepository taskActivityRepository,
                       UserRepository userRepository,
                       AccessControlService accessControl,
                       TaskActivityService activityService) {
        this.taskRepository = taskRepository;
        this.taskActivityRepository = taskActivityRepository;
        this.userRepository = userRepository;
        this.accessControl = accessControl;
        this.activityService = activityService;
    }

    // ---------------------------------------------------------------- queries

    public TaskResponse get(AppUserDetails principal, Long taskId) {
        Task task = loadTask(taskId);
        accessControl.requireProjectAccess(principal, task.getProject());
        return TaskResponse.from(task);
    }

    public PagedResponse<TaskResponse> listProjectTasks(AppUserDetails principal, Long projectId,
                                                        TaskStatus status, Long assigneeId,
                                                        TaskPriority priority, String search,
                                                        Pageable pageable) {
        accessControl.requireProjectAccess(principal, projectId);

        Specification<Task> spec = Specification.where(TaskSpecifications.inProject(projectId))
                .and(TaskSpecifications.hasStatus(status))
                .and(TaskSpecifications.hasAssignee(assigneeId))
                .and(TaskSpecifications.hasPriority(priority))
                .and(TaskSpecifications.titleContains(search));

        Page<Task> page = taskRepository.findAll(spec, pageable);
        return PagedResponse.from(page, TaskResponse::from);
    }

    public PagedResponse<TaskActivityResponse> listActivities(AppUserDetails principal, Long taskId,
                                                              Pageable pageable) {
        Task task = loadTask(taskId);
        accessControl.requireProjectAccess(principal, task.getProject());
        return PagedResponse.from(taskActivityRepository.findByTaskId(taskId, pageable),
                TaskActivityResponse::from);
    }

    // --------------------------------------------------------------- commands

    @Transactional
    public TaskResponse create(AppUserDetails principal, Long projectId, CreateTaskRequest request) {
        Project project = accessControl.requireProjectManagement(principal, projectId);
        User actor = currentUser(principal);

        Task task = new Task();
        task.setProject(project);
        task.setTitle(request.title().trim());
        task.setDescription(request.description());
        task.setPriority(request.priority() == null ? TaskPriority.MEDIUM : request.priority());
        task.setExpectedCompletionDate(request.expectedCompletionDate());
        task.setStatus(TaskStatus.TODO);
        task.setProgress(0);
        task.setCreatedBy(actor);

        User assignee = null;
        if (request.assigneeId() != null) {
            assignee = validateAssignee(project, request.assigneeId());
            task.setAssignee(assignee);
        }

        Task saved = taskRepository.save(task);
        activityService.record(saved, actor, ActivityType.TASK_CREATED,
                null, TaskStatus.TODO, null, 0, "Task created");
        if (assignee != null) {
            activityService.record(saved, actor, ActivityType.TASK_ASSIGNED,
                    "Assigned to " + assignee.getFullName());
        }

        return TaskResponse.from(saved);
    }

    @Transactional
    public TaskResponse update(AppUserDetails principal, Long taskId, UpdateTaskRequest request) {
        Task task = loadTask(taskId);
        accessControl.requireProjectManagement(principal, task.getProject());
        checkVersion(task, request.version());

        if (task.getStatus().isTerminal()) {
            throw new InvalidStatusTransitionException("An APPROVED task can no longer be edited");
        }

        task.setTitle(request.title().trim());
        task.setDescription(request.description());
        task.setPriority(request.priority());
        task.setExpectedCompletionDate(request.expectedCompletionDate());

        activityService.record(task, currentUser(principal), ActivityType.TASK_UPDATED, "Task details updated");
        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse assign(AppUserDetails principal, Long taskId, AssignTaskRequest request) {
        Task task = loadTask(taskId);
        accessControl.requireProjectManagement(principal, task.getProject());
        checkVersion(task, request.version());

        if (task.getStatus() == TaskStatus.APPROVED || task.getStatus() == TaskStatus.COMPLETED) {
            throw new InvalidStatusTransitionException(
                    "A task in status " + task.getStatus() + " can no longer be reassigned");
        }

        User assignee = validateAssignee(task.getProject(), request.assigneeId());
        if (task.isAssignedTo(assignee.getId())) {
            throw new BusinessRuleException("ALREADY_ASSIGNED",
                    "Task " + taskId + " is already assigned to user " + assignee.getId());
        }

        task.setAssignee(assignee);
        activityService.record(task, currentUser(principal), ActivityType.TASK_ASSIGNED,
                "Assigned to " + assignee.getFullName());
        return TaskResponse.from(task);
    }

    /** Explicit TODO/REJECTED → IN_PROGRESS move by the assigned engineer. */
    @Transactional
    public TaskResponse start(AppUserDetails principal, Long taskId) {
        Task task = loadTask(taskId);
        requireAssignee(principal, task);

        TaskStatus from = task.getStatus();
        if (!from.canTransitionTo(TaskStatus.IN_PROGRESS)) {
            throw new InvalidStatusTransitionException(from, TaskStatus.IN_PROGRESS);
        }

        task.setStatus(TaskStatus.IN_PROGRESS);
        activityService.record(task, currentUser(principal), ActivityType.TASK_STARTED,
                from, TaskStatus.IN_PROGRESS, task.getProgress(), task.getProgress(), "Work started");
        return TaskResponse.from(task);
    }

    /**
     * Progress update by the assigned engineer. Reporting any progress on a task
     * that has not been started (or was sent back for rework) starts it.
     */
    @Transactional
    public TaskResponse updateProgress(AppUserDetails principal, Long taskId,
                                       UpdateProgressRequest request) {
        Task task = loadTask(taskId);
        requireAssignee(principal, task);
        checkVersion(task, request.version());

        TaskStatus from = task.getStatus();
        if (!from.allowsProgressUpdate()) {
            throw new InvalidStatusTransitionException(
                    "Progress cannot be changed while the task is " + from);
        }

        User actor = currentUser(principal);
        int oldProgress = task.getProgress();
        int newProgress = request.progress();

        if (newProgress > 0 && from != TaskStatus.IN_PROGRESS) {
            task.setStatus(TaskStatus.IN_PROGRESS);
            activityService.record(task, actor, ActivityType.TASK_STARTED,
                    from, TaskStatus.IN_PROGRESS, oldProgress, oldProgress,
                    "Work started by reporting progress");
        }

        task.setProgress(newProgress);
        activityService.record(task, actor, ActivityType.PROGRESS_UPDATED,
                from, task.getStatus(), oldProgress, newProgress, request.note());

        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse complete(AppUserDetails principal, Long taskId, CompleteTaskRequest request) {
        Task task = loadTask(taskId);
        requireAssignee(principal, task);
        checkVersion(task, request.version());

        TaskStatus from = task.getStatus();
        if (!from.canTransitionTo(TaskStatus.COMPLETED)) {
            throw new InvalidStatusTransitionException(from, TaskStatus.COMPLETED);
        }

        int oldProgress = task.getProgress();
        task.setStatus(TaskStatus.COMPLETED);
        task.setProgress(100);
        task.setCompletedAt(Instant.now());

        activityService.record(task, currentUser(principal), ActivityType.TASK_COMPLETED,
                from, TaskStatus.COMPLETED, oldProgress, 100,
                request.note() == null ? "Marked as completed" : request.note());

        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse approve(AppUserDetails principal, Long taskId, ApproveTaskRequest request) {
        Task task = loadTask(taskId);
        accessControl.requireProjectManagement(principal, task.getProject());
        checkVersion(task, request.version());

        TaskStatus from = task.getStatus();
        if (!from.canTransitionTo(TaskStatus.APPROVED)) {
            throw new InvalidStatusTransitionException(from, TaskStatus.APPROVED);
        }

        User approver = currentUser(principal);
        task.setStatus(TaskStatus.APPROVED);
        task.setApprovedBy(approver);
        task.setApprovedAt(Instant.now());

        activityService.record(task, approver, ActivityType.TASK_APPROVED,
                from, TaskStatus.APPROVED, task.getProgress(), task.getProgress(),
                request.note() == null ? "Task approved" : request.note());

        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse reject(AppUserDetails principal, Long taskId, RejectTaskRequest request) {
        Task task = loadTask(taskId);
        accessControl.requireProjectManagement(principal, task.getProject());
        checkVersion(task, request.version());

        TaskStatus from = task.getStatus();
        if (!from.canTransitionTo(TaskStatus.REJECTED)) {
            throw new InvalidStatusTransitionException(from, TaskStatus.REJECTED);
        }

        User reviewer = currentUser(principal);
        task.setStatus(TaskStatus.REJECTED);
        task.setRejectedBy(reviewer);
        task.setRejectedAt(Instant.now());
        task.setRejectionReason(request.reason().trim());
        task.setCompletedAt(null);

        activityService.record(task, reviewer, ActivityType.TASK_REJECTED,
                from, TaskStatus.REJECTED, task.getProgress(), task.getProgress(),
                request.reason().trim());

        return TaskResponse.from(task);
    }

    // ---------------------------------------------------------------- helpers

    private Task loadTask(Long taskId) {
        return taskRepository.findWithDetailsById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
    }

    private User currentUser(AppUserDetails principal) {
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", principal.getId()));
    }

    /**
     * Only the engineer the task is assigned to may report work on it. Administrators
     * are allowed through as the "explicitly permitted" case.
     */
    private void requireAssignee(AppUserDetails principal, Task task) {
        accessControl.requireProjectAccess(principal, task.getProject());
        if (accessControl.isAdmin(principal)) {
            return;
        }
        if (task.getAssignee() == null) {
            throw new ForbiddenOperationException("Task " + task.getId() + " is not assigned to anyone yet");
        }
        if (!task.isAssignedTo(principal.getId())) {
            throw new ForbiddenOperationException(
                    "Task " + task.getId() + " is assigned to another engineer");
        }
    }

    /** The assignee must be an active site engineer who belongs to the project. */
    private User validateAssignee(Project project, Long assigneeId) {
        User assignee = userRepository.findById(assigneeId)
                .orElseThrow(() -> new ResourceNotFoundException("User", assigneeId));

        if (!assignee.isActive()) {
            throw new BusinessRuleException("INACTIVE_ASSIGNEE",
                    "User " + assigneeId + " is not active");
        }
        if (!assignee.hasRole(RoleName.SITE_ENGINEER)) {
            throw new BusinessRuleException("INVALID_ASSIGNEE_ROLE",
                    "Tasks can only be assigned to users with the SITE_ENGINEER role");
        }
        if (accessControl.findMembership(project.getId(), assigneeId).isEmpty()) {
            throw new BusinessRuleException("ASSIGNEE_NOT_A_MEMBER",
                    "User " + assigneeId + " is not a member of project " + project.getId());
        }
        return assignee;
    }

    private void checkVersion(Task task, Long submittedVersion) {
        if (submittedVersion != null && !Objects.equals(submittedVersion, task.getVersion())) {
            throw new StaleResourceException("Task", submittedVersion, task.getVersion());
        }
    }
}
