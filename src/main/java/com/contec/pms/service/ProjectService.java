package com.contec.pms.service;

import com.contec.pms.domain.entity.Project;
import com.contec.pms.domain.entity.ProjectMember;
import com.contec.pms.domain.entity.User;
import com.contec.pms.domain.enums.ProjectStatus;
import com.contec.pms.domain.enums.Role;
import com.contec.pms.exception.BusinessRuleException;
import com.contec.pms.exception.ResourceNotFoundException;
import com.contec.pms.repository.ProjectMemberRepository;
import com.contec.pms.repository.ProjectRepository;
import com.contec.pms.repository.UserRepository;
import com.contec.pms.security.AppUserDetails;
import com.contec.pms.web.dto.request.AddProjectMemberRequest;
import com.contec.pms.web.dto.request.CreateProjectRequest;
import com.contec.pms.web.dto.request.UpdateProjectRequest;
import com.contec.pms.web.dto.response.PagedResponse;
import com.contec.pms.web.dto.response.ProjectMemberResponse;
import com.contec.pms.web.dto.response.ProjectResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final AccessControlService accessControl;

    public ProjectService(ProjectRepository projectRepository,
                          ProjectMemberRepository projectMemberRepository,
                          UserRepository userRepository,
                          AccessControlService accessControl) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.userRepository = userRepository;
        this.accessControl = accessControl;
    }

    @Transactional
    public ProjectResponse create(AppUserDetails principal, CreateProjectRequest request) {
        validateDates(request.startDate(), request.expectedCompletionDate());
        User creator = getUser(principal.getId());

        Project project = new Project();
        project.setName(request.name().trim());
        project.setDescription(request.description());
        project.setLocation(request.location());
        project.setStartDate(request.startDate());
        project.setExpectedCompletionDate(request.expectedCompletionDate());
        project.setStatus(request.status() == null ? ProjectStatus.PLANNED : request.status());
        project.setCreatedBy(creator);
        Project saved = projectRepository.save(project);

        // a manager who creates a project joins it, so they can manage it straight away
        if (creator.hasRole(Role.PROJECT_MANAGER)) {
            projectMemberRepository.save(new ProjectMember(saved, creator));
        }
        if (request.managerId() != null && !request.managerId().equals(creator.getId())) {
            User manager = getUser(request.managerId());
            if (!manager.hasRole(Role.PROJECT_MANAGER)) {
                throw new BusinessRuleException("INVALID_PROJECT_MANAGER",
                        "User " + manager.getId() + " is not a project manager");
            }
            projectMemberRepository.save(new ProjectMember(saved, manager));
        }

        return ProjectResponse.from(saved);
    }

    @Transactional
    public ProjectResponse update(AppUserDetails principal, Long projectId, UpdateProjectRequest request) {
        Project project = accessControl.requireProjectManagement(principal, projectId);
        validateDates(request.startDate(), request.expectedCompletionDate());

        project.setName(request.name().trim());
        project.setDescription(request.description());
        project.setLocation(request.location());
        project.setStartDate(request.startDate());
        project.setExpectedCompletionDate(request.expectedCompletionDate());
        project.setStatus(request.status());

        return ProjectResponse.from(project);
    }

    public ProjectResponse get(AppUserDetails principal, Long projectId) {
        return ProjectResponse.from(accessControl.requireProjectAccess(principal, projectId));
    }

    public PagedResponse<ProjectResponse> list(AppUserDetails principal, ProjectStatus status,
                                               Pageable pageable) {
        Long memberId = accessControl.isAdmin(principal) ? null : principal.getId();
        return PagedResponse.from(projectRepository.findVisible(memberId, status, pageable),
                ProjectResponse::from);
    }

    @Transactional
    public ProjectMemberResponse addMember(AppUserDetails principal, Long projectId,
                                           AddProjectMemberRequest request) {
        Project project = accessControl.requireProjectManagement(principal, projectId);
        User user = getUser(request.userId());

        if (!user.isActive()) {
            throw new BusinessRuleException("INACTIVE_USER", "User " + user.getId() + " is not active");
        }
        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, user.getId())) {
            throw new BusinessRuleException("ALREADY_A_MEMBER",
                    "User " + user.getId() + " is already on project " + projectId);
        }

        return ProjectMemberResponse.from(projectMemberRepository.save(new ProjectMember(project, user)));
    }

    public List<ProjectMemberResponse> listMembers(AppUserDetails principal, Long projectId) {
        accessControl.requireProjectAccess(principal, projectId);
        return projectMemberRepository.findByProjectIdOrderByAddedAtAsc(projectId).stream()
                .map(ProjectMemberResponse::from)
                .toList();
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private void validateDates(LocalDate start, LocalDate expectedCompletion) {
        if (expectedCompletion.isBefore(start)) {
            throw new BusinessRuleException("INVALID_PROJECT_DATES",
                    "expectedCompletionDate must not be before startDate");
        }
    }
}
