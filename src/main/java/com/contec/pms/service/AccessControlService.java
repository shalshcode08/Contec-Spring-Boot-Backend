package com.contec.pms.service;

import com.contec.pms.domain.entity.Project;
import com.contec.pms.domain.enums.Role;
import com.contec.pms.exception.ForbiddenOperationException;
import com.contec.pms.exception.ResourceNotFoundException;
import com.contec.pms.repository.ProjectMemberRepository;
import com.contec.pms.repository.ProjectRepository;
import com.contec.pms.security.AppUserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// project access comes from membership, not from the global role alone; ADMIN bypasses it
@Service
@Transactional(readOnly = true)
public class AccessControlService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    public AccessControlService(ProjectRepository projectRepository,
                                ProjectMemberRepository projectMemberRepository) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
    }

    public boolean isAdmin(AppUserDetails principal) {
        return principal.hasRole(Role.ADMIN);
    }

    public boolean isMember(Long projectId, Long userId) {
        return projectMemberRepository.existsByProjectIdAndUserId(projectId, userId);
    }

    public Project getProjectOrThrow(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
    }

    public Project requireProjectAccess(AppUserDetails principal, Long projectId) {
        Project project = getProjectOrThrow(projectId);
        requireProjectAccess(principal, project);
        return project;
    }

    public void requireProjectAccess(AppUserDetails principal, Project project) {
        if (!isAdmin(principal) && !isMember(project.getId(), principal.getId())) {
            throw new ForbiddenOperationException("You are not assigned to project " + project.getId());
        }
    }

    public Project requireProjectManagement(AppUserDetails principal, Long projectId) {
        Project project = getProjectOrThrow(projectId);
        requireProjectManagement(principal, project);
        return project;
    }

    public void requireProjectManagement(AppUserDetails principal, Project project) {
        if (isAdmin(principal)) {
            return;
        }
        if (!principal.hasRole(Role.PROJECT_MANAGER) || !isMember(project.getId(), principal.getId())) {
            throw new ForbiddenOperationException(
                    "Only a project manager assigned to project " + project.getId() + " may do this");
        }
    }
}
