package com.contec.pms.web.controller;

import com.contec.pms.security.AppUserDetails;
import com.contec.pms.service.TaskService;
import com.contec.pms.web.dto.request.ApproveTaskRequest;
import com.contec.pms.web.dto.request.AssignTaskRequest;
import com.contec.pms.web.dto.request.CompleteTaskRequest;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
@Tag(name = "Tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/{taskId}")
    @Operation(summary = "Get a task")
    public ResponseEntity<TaskResponse> get(@AuthenticationPrincipal AppUserDetails principal,
                                            @PathVariable Long taskId) {
        return ResponseEntity.ok(taskService.get(principal, taskId));
    }

    @PutMapping("/{taskId}")
    @Operation(summary = "Update task details (project manager)")
    public ResponseEntity<TaskResponse> update(@AuthenticationPrincipal AppUserDetails principal,
                                               @PathVariable Long taskId,
                                               @Valid @RequestBody UpdateTaskRequest request) {
        return ResponseEntity.ok(taskService.update(principal, taskId, request));
    }

    @PostMapping("/{taskId}/assign")
    @Operation(summary = "Assign the task to a site engineer on the project (project manager)")
    public ResponseEntity<TaskResponse> assign(@AuthenticationPrincipal AppUserDetails principal,
                                               @PathVariable Long taskId,
                                               @Valid @RequestBody AssignTaskRequest request) {
        return ResponseEntity.ok(taskService.assign(principal, taskId, request));
    }

    @PostMapping("/{taskId}/start")
    @Operation(summary = "Move an assigned task to IN_PROGRESS (assigned engineer)")
    public ResponseEntity<TaskResponse> start(@AuthenticationPrincipal AppUserDetails principal,
                                              @PathVariable Long taskId) {
        return ResponseEntity.ok(taskService.start(principal, taskId));
    }

    @PatchMapping("/{taskId}/progress")
    @Operation(summary = "Report progress between 0 and 100 (assigned engineer)")
    public ResponseEntity<TaskResponse> updateProgress(@AuthenticationPrincipal AppUserDetails principal,
                                                       @PathVariable Long taskId,
                                                       @Valid @RequestBody UpdateProgressRequest request) {
        return ResponseEntity.ok(taskService.updateProgress(principal, taskId, request));
    }

    @PostMapping("/{taskId}/complete")
    @Operation(summary = "Mark the task completed (assigned engineer)")
    public ResponseEntity<TaskResponse> complete(@AuthenticationPrincipal AppUserDetails principal,
                                                 @PathVariable Long taskId,
                                                 @Valid @RequestBody CompleteTaskRequest request) {
        return ResponseEntity.ok(taskService.complete(principal, taskId, request));
    }

    @PostMapping("/{taskId}/approve")
    @Operation(summary = "Approve a completed task (project manager)")
    public ResponseEntity<TaskResponse> approve(@AuthenticationPrincipal AppUserDetails principal,
                                                @PathVariable Long taskId,
                                                @Valid @RequestBody ApproveTaskRequest request) {
        return ResponseEntity.ok(taskService.approve(principal, taskId, request));
    }

    @PostMapping("/{taskId}/reject")
    @Operation(summary = "Reject a completed task with a reason (project manager)")
    public ResponseEntity<TaskResponse> reject(@AuthenticationPrincipal AppUserDetails principal,
                                               @PathVariable Long taskId,
                                               @Valid @RequestBody RejectTaskRequest request) {
        return ResponseEntity.ok(taskService.reject(principal, taskId, request));
    }

    @GetMapping("/{taskId}/activities")
    @Operation(summary = "Activity history: what happened, who did it and when")
    public ResponseEntity<PagedResponse<TaskActivityResponse>> activities(
            @AuthenticationPrincipal AppUserDetails principal,
            @PathVariable Long taskId,
            @ParameterObject @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(taskService.listActivities(principal, taskId, pageable));
    }
}
