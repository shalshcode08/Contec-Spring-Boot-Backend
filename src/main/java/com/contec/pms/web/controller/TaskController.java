package com.contec.pms.web.controller;

import com.contec.pms.domain.enums.TaskPriority;
import com.contec.pms.domain.enums.TaskStatus;
import com.contec.pms.security.AppUserDetails;
import com.contec.pms.service.TaskService;
import com.contec.pms.web.dto.request.AssignTaskRequest;
import com.contec.pms.web.dto.request.CreateTaskRequest;
import com.contec.pms.web.dto.request.RejectTaskRequest;
import com.contec.pms.web.dto.request.UpdateProgressRequest;
import com.contec.pms.web.dto.request.UpdateTaskRequest;
import com.contec.pms.web.dto.response.PagedResponse;
import com.contec.pms.web.dto.response.TaskActivityResponse;
import com.contec.pms.web.dto.response.TaskResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/projects/{projectId}/tasks")
    @Operation(summary = "Create a task (administrators and the project's manager)")
    public ResponseEntity<TaskResponse> create(@AuthenticationPrincipal AppUserDetails principal,
                                               @PathVariable Long projectId,
                                               @Valid @RequestBody CreateTaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(taskService.create(principal, projectId, request));
    }

    @GetMapping("/projects/{projectId}/tasks")
    @Operation(summary = "List project tasks with paging, sorting and filtering")
    public PagedResponse<TaskResponse> list(
            @AuthenticationPrincipal AppUserDetails principal,
            @PathVariable Long projectId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) Long assigneeId,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return taskService.listProjectTasks(principal, projectId, status, priority, assigneeId, pageable);
    }

    @GetMapping("/tasks/{taskId}")
    @Operation(summary = "Get a task")
    public TaskResponse get(@AuthenticationPrincipal AppUserDetails principal,
                            @PathVariable Long taskId) {
        return taskService.get(principal, taskId);
    }

    @PutMapping("/tasks/{taskId}")
    @Operation(summary = "Update task details (project manager)")
    public TaskResponse update(@AuthenticationPrincipal AppUserDetails principal,
                               @PathVariable Long taskId,
                               @Valid @RequestBody UpdateTaskRequest request) {
        return taskService.update(principal, taskId, request);
    }

    @PostMapping("/tasks/{taskId}/assign")
    @Operation(summary = "Assign the task to a site engineer on the project (project manager)")
    public TaskResponse assign(@AuthenticationPrincipal AppUserDetails principal,
                               @PathVariable Long taskId,
                               @Valid @RequestBody AssignTaskRequest request) {
        return taskService.assign(principal, taskId, request);
    }

    @PostMapping("/tasks/{taskId}/start")
    @Operation(summary = "Move an assigned task to IN_PROGRESS (assigned engineer)")
    public TaskResponse start(@AuthenticationPrincipal AppUserDetails principal,
                              @PathVariable Long taskId) {
        return taskService.start(principal, taskId);
    }

    @PatchMapping("/tasks/{taskId}/progress")
    @Operation(summary = "Report progress between 0 and 100 (assigned engineer)")
    public TaskResponse updateProgress(@AuthenticationPrincipal AppUserDetails principal,
                                       @PathVariable Long taskId,
                                       @Valid @RequestBody UpdateProgressRequest request) {
        return taskService.updateProgress(principal, taskId, request);
    }

    @PostMapping("/tasks/{taskId}/complete")
    @Operation(summary = "Mark the task completed (assigned engineer)")
    public TaskResponse complete(@AuthenticationPrincipal AppUserDetails principal,
                                 @PathVariable Long taskId) {
        return taskService.complete(principal, taskId);
    }

    @PostMapping("/tasks/{taskId}/approve")
    @Operation(summary = "Approve a completed task (project manager)")
    public TaskResponse approve(@AuthenticationPrincipal AppUserDetails principal,
                                @PathVariable Long taskId) {
        return taskService.approve(principal, taskId);
    }

    @PostMapping("/tasks/{taskId}/reject")
    @Operation(summary = "Reject a completed task with a reason (project manager)")
    public TaskResponse reject(@AuthenticationPrincipal AppUserDetails principal,
                               @PathVariable Long taskId,
                               @Valid @RequestBody RejectTaskRequest request) {
        return taskService.reject(principal, taskId, request);
    }

    @GetMapping("/tasks/{taskId}/activities")
    @Operation(summary = "Activity history: what happened, who did it and when")
    public PagedResponse<TaskActivityResponse> activities(
            @AuthenticationPrincipal AppUserDetails principal,
            @PathVariable Long taskId,
            @ParameterObject @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return taskService.listActivities(principal, taskId, pageable);
    }
}
