package com.contec.pms.web.controller;

import com.contec.pms.domain.enums.ProjectStatus;
import com.contec.pms.security.AppUserDetails;
import com.contec.pms.service.ProjectMemberService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@Tag(name = "Projects")
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectMemberService projectMemberService;

    public ProjectController(ProjectService projectService, ProjectMemberService projectMemberService) {
        this.projectService = projectService;
        this.projectMemberService = projectMemberService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
    @Operation(summary = "Create a project; the creating manager becomes its project manager")
    public ResponseEntity<ProjectResponse> create(@AuthenticationPrincipal AppUserDetails principal,
                                                  @Valid @RequestBody CreateProjectRequest request) {
        ProjectResponse created = projectService.create(principal, request);
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/projects/{id}").build(created.id()))
                .body(created);
    }

    @PutMapping("/{projectId}")
    @Operation(summary = "Update a project (administrators and the project's manager)")
    public ResponseEntity<ProjectResponse> update(@AuthenticationPrincipal AppUserDetails principal,
                                                  @PathVariable Long projectId,
                                                  @Valid @RequestBody UpdateProjectRequest request) {
        return ResponseEntity.ok(projectService.update(principal, projectId, request));
    }

    @GetMapping("/{projectId}")
    @Operation(summary = "Get a project the caller is assigned to")
    public ResponseEntity<ProjectResponse> get(@AuthenticationPrincipal AppUserDetails principal,
                                               @PathVariable Long projectId) {
        return ResponseEntity.ok(projectService.get(principal, projectId));
    }

    @GetMapping
    @Operation(summary = "List projects; non-administrators see only their own projects")
    public ResponseEntity<PagedResponse<ProjectResponse>> list(
            @AuthenticationPrincipal AppUserDetails principal,
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(required = false) String search,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(projectService.list(principal, status, search, pageable));
    }

    @PostMapping("/{projectId}/members")
    @Operation(summary = "Assign a user to the project")
    public ResponseEntity<ProjectMemberResponse> addMember(
            @AuthenticationPrincipal AppUserDetails principal,
            @PathVariable Long projectId,
            @Valid @RequestBody AddProjectMemberRequest request) {
        return ResponseEntity.status(201).body(projectMemberService.addMember(principal, projectId, request));
    }

    @GetMapping("/{projectId}/members")
    @Operation(summary = "List the project's members")
    public ResponseEntity<List<ProjectMemberResponse>> listMembers(
            @AuthenticationPrincipal AppUserDetails principal,
            @PathVariable Long projectId) {
        return ResponseEntity.ok(projectMemberService.listMembers(principal, projectId));
    }

    @DeleteMapping("/{projectId}/members/{userId}")
    @Operation(summary = "Remove a member who has no unfinished tasks left")
    public ResponseEntity<Void> removeMember(@AuthenticationPrincipal AppUserDetails principal,
                                             @PathVariable Long projectId,
                                             @PathVariable Long userId) {
        projectMemberService.removeMember(principal, projectId, userId);
        return ResponseEntity.noContent().build();
    }
}
