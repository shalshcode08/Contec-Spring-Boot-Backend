package com.contec.pms.web.controller;

import com.contec.pms.domain.enums.TaskPriority;
import com.contec.pms.domain.enums.TaskStatus;
import com.contec.pms.security.AppUserDetails;
import com.contec.pms.service.TaskService;
import com.contec.pms.web.dto.request.CreateTaskRequest;
import com.contec.pms.web.dto.response.PagedResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/projects/{projectId}/tasks")
@Tag(name = "Tasks")
public class ProjectTaskController {

    private final TaskService taskService;

    public ProjectTaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    @Operation(summary = "Create a task in the project (administrators and the project's manager)")
    public ResponseEntity<TaskResponse> create(@AuthenticationPrincipal AppUserDetails principal,
                                               @PathVariable Long projectId,
                                               @Valid @RequestBody CreateTaskRequest request) {
        TaskResponse created = taskService.create(principal, projectId, request);
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/tasks/{id}").build(created.id()))
                .body(created);
    }

    @GetMapping
    @Operation(summary = "List the project's tasks with paging, sorting and filtering",
            description = "Filter by status, assignee and priority; sort with e.g. ?sort=priority,desc")
    public ResponseEntity<PagedResponse<TaskResponse>> list(
            @AuthenticationPrincipal AppUserDetails principal,
            @PathVariable Long projectId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) String search,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(taskService.listProjectTasks(
                principal, projectId, status, assigneeId, priority, search, pageable));
    }
}
