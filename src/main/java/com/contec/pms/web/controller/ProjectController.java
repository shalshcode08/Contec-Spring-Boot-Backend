package com.contec.pms.web.controller;

import com.contec.pms.domain.enums.ProjectStatus;
import com.contec.pms.security.AppUserDetails;
import com.contec.pms.service.ProjectService;
import com.contec.pms.web.dto.request.AddProjectMemberRequest;
import com.contec.pms.web.dto.request.CreateProjectRequest;
import com.contec.pms.web.dto.request.UpdateProjectRequest;
import com.contec.pms.web.dto.response.PagedResponse;
import com.contec.pms.web.dto.response.ProjectMemberResponse;
import com.contec.pms.web.dto.response.ProjectResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@Tag(name = "Projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
    @Operation(summary = "Create a project")
    public ResponseEntity<ProjectResponse> create(@AuthenticationPrincipal AppUserDetails principal,
                                                  @Valid @RequestBody CreateProjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.create(principal, request));
    }

    @PutMapping("/{projectId}")
    @Operation(summary = "Update a project (administrators and the project's manager)")
    public ProjectResponse update(@AuthenticationPrincipal AppUserDetails principal,
                                  @PathVariable Long projectId,
                                  @Valid @RequestBody UpdateProjectRequest request) {
        return projectService.update(principal, projectId, request);
    }

    @GetMapping("/{projectId}")
    @Operation(summary = "Get a project the caller is assigned to")
    public ProjectResponse get(@AuthenticationPrincipal AppUserDetails principal,
                               @PathVariable Long projectId) {
        return projectService.get(principal, projectId);
    }

    @GetMapping
    @Operation(summary = "List projects; non-administrators see only their own")
    public PagedResponse<ProjectResponse> list(
            @AuthenticationPrincipal AppUserDetails principal,
            @RequestParam(required = false) ProjectStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return projectService.list(principal, status, pageable);
    }

    @PostMapping("/{projectId}/members")
    @Operation(summary = "Assign a user to the project")
    public ResponseEntity<ProjectMemberResponse> addMember(
            @AuthenticationPrincipal AppUserDetails principal,
            @PathVariable Long projectId,
            @Valid @RequestBody AddProjectMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectService.addMember(principal, projectId, request));
    }

    @GetMapping("/{projectId}/members")
    @Operation(summary = "List the project's members")
    public List<ProjectMemberResponse> listMembers(@AuthenticationPrincipal AppUserDetails principal,
                                                   @PathVariable Long projectId) {
        return projectService.listMembers(principal, projectId);
    }
}
