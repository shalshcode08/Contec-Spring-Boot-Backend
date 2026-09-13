package com.contec.pms.service;

import com.contec.pms.domain.entity.Project;
import com.contec.pms.domain.entity.ProjectMember;
import com.contec.pms.domain.enums.RoleName;
import com.contec.pms.exception.ForbiddenOperationException;
import com.contec.pms.exception.ResourceNotFoundException;
import com.contec.pms.repository.ProjectMemberRepository;
import com.contec.pms.repository.ProjectRepository;
import com.contec.pms.security.AppUserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * The single place that answers "may this user touch this project?".
 *
 * <p>Global roles alone grant nothing at project level: a project manager may only
 * manage projects where they are a member with {@code MANAGER}, and an engineer may
 * only see projects they belong to. Administrators bypass membership.
 */
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
        return principal.hasRole(RoleName.ADMIN);
    }

    public Project getProjectOrThrow(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
    }

    public Optional<ProjectMember> findMembership(Long projectId, Long userId) {
        return projectMemberRepository.findByProjectIdAndUserId(projectId, userId);
    }

    /** The caller must be an administrator or a member of the project. */
    public Project requireProjectAccess(AppUserDetails principal, Long projectId) {
        Project project = getProjectOrThrow(projectId);
        requireProjectAccess(principal, project);
        return project;
    }

    public void requireProjectAccess(AppUserDetails principal, Project project) {
        if (isAdmin(principal)) {
            return;
        }
        if (findMembership(project.getId(), principal.getId()).isEmpty()) {
            throw new ForbiddenOperationException(
                    "You are not assigned to project " + project.getId());
        }
    }

    /** The caller must be an administrator or the project's MANAGER. */
    public Project requireProjectManagement(AppUserDetails principal, Long projectId) {
        Project project = getProjectOrThrow(projectId);
        requireProjectManagement(principal, project);
        return project;
    }

    public void requireProjectManagement(AppUserDetails principal, Project project) {
        if (isAdmin(principal)) {
            return;
        }
        boolean manages = findMembership(project.getId(), principal.getId())
                .map(ProjectMember::isManager)
                .orElse(false);
        if (!manages) {
            throw new ForbiddenOperationException(
                    "Only a project manager of project " + project.getId() + " may perform this operation");
        }
    }

    public boolean isProjectManager(AppUserDetails principal, Long projectId) {
        return isAdmin(principal)
                || findMembership(projectId, principal.getId()).map(ProjectMember::isManager).orElse(false);
    }
}
