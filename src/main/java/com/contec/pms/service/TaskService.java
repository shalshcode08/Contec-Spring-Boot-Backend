package com.contec.pms.service;

import com.contec.pms.domain.entity.Project;
import com.contec.pms.domain.entity.Task;
import com.contec.pms.domain.entity.TaskActivity;
import com.contec.pms.domain.entity.User;
import com.contec.pms.domain.enums.ActivityType;
import com.contec.pms.domain.enums.Role;
import com.contec.pms.domain.enums.TaskPriority;
import com.contec.pms.domain.enums.TaskStatus;
import com.contec.pms.exception.BusinessRuleException;
import com.contec.pms.exception.ForbiddenOperationException;
import com.contec.pms.exception.InvalidStatusTransitionException;
import com.contec.pms.exception.ResourceNotFoundException;
import com.contec.pms.exception.StaleResourceException;
import com.contec.pms.repository.TaskActivityRepository;
import com.contec.pms.repository.TaskRepository;
import com.contec.pms.repository.UserRepository;
import com.contec.pms.security.AppUserDetails;
import com.contec.pms.web.dto.request.AssignTaskRequest;
import com.contec.pms.web.dto.request.CreateTaskRequest;
import com.contec.pms.web.dto.request.RejectTaskRequest;
import com.contec.pms.web.dto.request.UpdateProgressRequest;
import com.contec.pms.web.dto.request.UpdateTaskRequest;
import com.contec.pms.web.dto.response.PagedResponse;
import com.contec.pms.web.dto.response.TaskActivityResponse;
import com.contec.pms.web.dto.response.TaskResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskActivityRepository taskActivityRepository;
    private final UserRepository userRepository;
    private final AccessControlService accessControl;

    public TaskService(TaskRepository taskRepository,
                       TaskActivityRepository taskActivityRepository,
                       UserRepository userRepository,
                       AccessControlService accessControl) {
        this.taskRepository = taskRepository;
        this.taskActivityRepository = taskActivityRepository;
        this.userRepository = userRepository;
        this.accessControl = accessControl;
    }

    public TaskResponse get(AppUserDetails principal, Long taskId) {
        Task task = loadTask(taskId);
        accessControl.requireProjectAccess(principal, task.getProject());
        return TaskResponse.from(task);
    }

    public PagedResponse<TaskResponse> listProjectTasks(AppUserDetails principal, Long projectId,
                                                        TaskStatus status, TaskPriority priority,
                                                        Long assigneeId, Pageable pageable) {
        accessControl.requireProjectAccess(principal, projectId);
        return PagedResponse.from(
                taskRepository.findProjectTasks(projectId, status, priority, assigneeId, pageable),
                TaskResponse::from);
    }

    public PagedResponse<TaskActivityResponse> listActivities(AppUserDetails principal, Long taskId,
                                                              Pageable pageable) {
        Task task = loadTask(taskId);
        accessControl.requireProjectAccess(principal, task.getProject());
        return PagedResponse.from(taskActivityRepository.findByTaskId(taskId, pageable),
                TaskActivityResponse::from);
    }

    @Transactional
    public TaskResponse create(AppUserDetails principal, Long projectId, CreateTaskRequest request) {
        Project project = accessControl.requireProjectManagement(principal, projectId);
        User actor = getUser(principal.getId());

        Task task = new Task();
        task.setProject(project);
        task.setTitle(request.title().trim());
        task.setDescription(request.description());
        task.setPriority(request.priority() == null ? TaskPriority.MEDIUM : request.priority());
        task.setExpectedCompletionDate(request.expectedCompletionDate());
        task.setStatus(TaskStatus.TODO);
        task.setProgress(0);
        task.setCreatedBy(actor);

        User assignee = request.assigneeId() == null ? null : validateAssignee(project, request.assigneeId());
        task.setAssignee(assignee);

        Task saved = taskRepository.save(task);
        log(saved, actor, ActivityType.TASK_CREATED, null, TaskStatus.TODO, null, 0, "Task created");
        if (assignee != null) {
            log(saved, actor, ActivityType.TASK_ASSIGNED, "Assigned to " + assignee.getFullName());
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

        log(task, getUser(principal.getId()), ActivityType.TASK_UPDATED, "Task details updated");
        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse assign(AppUserDetails principal, Long taskId, AssignTaskRequest request) {
        Task task = loadTask(taskId);
        accessControl.requireProjectManagement(principal, task.getProject());
        checkVersion(task, request.version());

        if (task.getStatus() == TaskStatus.COMPLETED || task.getStatus() == TaskStatus.APPROVED) {
            throw new InvalidStatusTransitionException(
                    "A task in status " + task.getStatus() + " can no longer be reassigned");
        }

        User assignee = validateAssignee(task.getProject(), request.assigneeId());
        task.setAssignee(assignee);

        log(task, getUser(principal.getId()), ActivityType.TASK_ASSIGNED,
                "Assigned to " + assignee.getFullName());
        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse start(AppUserDetails principal, Long taskId) {
        Task task = loadTask(taskId);
        requireAssignee(principal, task);

        TaskStatus from = task.getStatus();
        if (!from.canTransitionTo(TaskStatus.IN_PROGRESS)) {
            throw new InvalidStatusTransitionException(from, TaskStatus.IN_PROGRESS);
        }
        task.setStatus(TaskStatus.IN_PROGRESS);

        log(task, getUser(principal.getId()), ActivityType.TASK_STARTED,
                from, TaskStatus.IN_PROGRESS, task.getProgress(), task.getProgress(), "Work started");
        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse updateProgress(AppUserDetails principal, Long taskId,
                                       UpdateProgressRequest request) {
        Task task = loadTask(taskId);
        requireAssignee(principal, task);
        checkVersion(task, request.version());

        TaskStatus from = task.getStatus();
        if (!from.allowsProgressUpdate()) {
            throw new InvalidStatusTransitionException("Progress cannot be changed while the task is " + from);
        }

        User actor = getUser(principal.getId());
        int oldProgress = task.getProgress();

        // reporting progress on a TODO or reworked task starts it
        if (request.progress() > 0 && from != TaskStatus.IN_PROGRESS) {
            task.setStatus(TaskStatus.IN_PROGRESS);
            log(task, actor, ActivityType.TASK_STARTED, from, TaskStatus.IN_PROGRESS,
                    oldProgress, oldProgress, "Work started by reporting progress");
        }

        task.setProgress(request.progress());
        log(task, actor, ActivityType.PROGRESS_UPDATED, from, task.getStatus(),
                oldProgress, request.progress(), request.note());
        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse complete(AppUserDetails principal, Long taskId) {
        Task task = loadTask(taskId);
        requireAssignee(principal, task);

        TaskStatus from = task.getStatus();
        if (!from.canTransitionTo(TaskStatus.COMPLETED)) {
            throw new InvalidStatusTransitionException(from, TaskStatus.COMPLETED);
        }

        int oldProgress = task.getProgress();
        task.setStatus(TaskStatus.COMPLETED);
        task.setProgress(100);
        task.setCompletedAt(Instant.now());

        log(task, getUser(principal.getId()), ActivityType.TASK_COMPLETED,
                from, TaskStatus.COMPLETED, oldProgress, 100, "Marked as completed");
        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse approve(AppUserDetails principal, Long taskId) {
        Task task = loadTask(taskId);
        accessControl.requireProjectManagement(principal, task.getProject());

        TaskStatus from = task.getStatus();
        if (!from.canTransitionTo(TaskStatus.APPROVED)) {
            throw new InvalidStatusTransitionException(from, TaskStatus.APPROVED);
        }

        User approver = getUser(principal.getId());
        task.setStatus(TaskStatus.APPROVED);
        task.setApprovedBy(approver);
        task.setApprovedAt(Instant.now());

        log(task, approver, ActivityType.TASK_APPROVED, from, TaskStatus.APPROVED,
                task.getProgress(), task.getProgress(), "Task approved");
        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse reject(AppUserDetails principal, Long taskId, RejectTaskRequest request) {
        Task task = loadTask(taskId);
        accessControl.requireProjectManagement(principal, task.getProject());

        TaskStatus from = task.getStatus();
        if (!from.canTransitionTo(TaskStatus.REJECTED)) {
            throw new InvalidStatusTransitionException(from, TaskStatus.REJECTED);
        }

        User reviewer = getUser(principal.getId());
        String reason = request.reason().trim();
        task.setStatus(TaskStatus.REJECTED);
        task.setRejectedBy(reviewer);
        task.setRejectedAt(Instant.now());
        task.setRejectionReason(reason);
        task.setCompletedAt(null);

        log(task, reviewer, ActivityType.TASK_REJECTED, from, TaskStatus.REJECTED,
                task.getProgress(), task.getProgress(), reason);
        return TaskResponse.from(task);
    }

    private void log(Task task, User actor, ActivityType type, String detail) {
        taskActivityRepository.save(new TaskActivity(task, actor, type, detail));
    }

    private void log(Task task, User actor, ActivityType type, TaskStatus oldStatus, TaskStatus newStatus,
                     Integer oldProgress, Integer newProgress, String detail) {
        taskActivityRepository.save(new TaskActivity(task, actor, type, oldStatus, newStatus,
                oldProgress, newProgress, detail));
    }

    private Task loadTask(Long taskId) {
        return taskRepository.findWithDetailsById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    // only the assigned engineer may report work; ADMIN is the explicitly permitted exception
    private void requireAssignee(AppUserDetails principal, Task task) {
        accessControl.requireProjectAccess(principal, task.getProject());
        if (accessControl.isAdmin(principal)) {
            return;
        }
        if (!task.isAssignedTo(principal.getId())) {
            throw new ForbiddenOperationException("Task " + task.getId() + " is not assigned to you");
        }
    }

    private User validateAssignee(Project project, Long assigneeId) {
        User assignee = getUser(assigneeId);
        if (!assignee.isActive()) {
            throw new BusinessRuleException("INACTIVE_ASSIGNEE", "User " + assigneeId + " is not active");
        }
        if (!assignee.hasRole(Role.SITE_ENGINEER)) {
            throw new BusinessRuleException("INVALID_ASSIGNEE_ROLE",
                    "Tasks can only be assigned to a SITE_ENGINEER");
        }
        if (!accessControl.isMember(project.getId(), assigneeId)) {
            throw new BusinessRuleException("ASSIGNEE_NOT_A_MEMBER",
                    "User " + assigneeId + " is not a member of project " + project.getId());
        }
        return assignee;
    }

    private void checkVersion(Task task, Long submittedVersion) {
        if (!Objects.equals(submittedVersion, task.getVersion())) {
            throw new StaleResourceException("Task", submittedVersion, task.getVersion());
        }
    }
}
