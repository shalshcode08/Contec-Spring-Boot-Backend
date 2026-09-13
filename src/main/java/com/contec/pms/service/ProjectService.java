package com.contec.pms.service;

import com.contec.pms.domain.entity.Project;
import com.contec.pms.domain.entity.ProjectMember;
import com.contec.pms.domain.entity.User;
import com.contec.pms.domain.enums.ProjectMemberRole;
import com.contec.pms.domain.enums.ProjectStatus;
import com.contec.pms.domain.enums.RoleName;
import com.contec.pms.exception.BusinessRuleException;
import com.contec.pms.exception.ResourceNotFoundException;
import com.contec.pms.exception.StaleResourceException;
import com.contec.pms.repository.ProjectMemberRepository;
import com.contec.pms.repository.ProjectRepository;
import com.contec.pms.repository.ProjectSpecifications;
import com.contec.pms.repository.UserRepository;
import com.contec.pms.security.AppUserDetails;
import com.contec.pms.web.dto.request.CreateProjectRequest;
import com.contec.pms.web.dto.request.UpdateProjectRequest;
import com.contec.pms.web.dto.response.PagedResponse;
import com.contec.pms.web.dto.response.ProjectResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Objects;

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

        User creator = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", principal.getId()));

        Project project = new Project();
        project.setName(request.name().trim());
        project.setDescription(request.description());
        project.setLocation(request.location());
        project.setStartDate(request.startDate());
        project.setExpectedCompletionDate(request.expectedCompletionDate());
        project.setStatus(request.status() == null ? ProjectStatus.PLANNED : request.status());
        project.setCreatedBy(creator);
        Project saved = projectRepository.save(project);

        // a manager who creates a project manages it; an admin may nominate one
        if (principal.hasRole(RoleName.PROJECT_MANAGER)) {
            projectMemberRepository.save(
                    new ProjectMember(saved, creator, ProjectMemberRole.MANAGER, creator));
        }

        if (request.managerId() != null && !request.managerId().equals(creator.getId())) {
            User manager = userRepository.findById(request.managerId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", request.managerId()));
            if (!manager.hasRole(RoleName.PROJECT_MANAGER)) {
                throw new BusinessRuleException("INVALID_PROJECT_MANAGER",
                        "User " + manager.getId() + " does not have the PROJECT_MANAGER role");
            }
            projectMemberRepository.save(
                    new ProjectMember(saved, manager, ProjectMemberRole.MANAGER, creator));
        }

        return ProjectResponse.from(saved);
    }

    @Transactional
    public ProjectResponse update(AppUserDetails principal, Long projectId, UpdateProjectRequest request) {
        Project project = accessControl.requireProjectManagement(principal, projectId);
        checkVersion(project, request.version());
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
                                               String search, Pageable pageable) {
        Specification<Project> spec = Specification.where(ProjectSpecifications.hasStatus(status))
                .and(ProjectSpecifications.nameContains(search));
        if (!accessControl.isAdmin(principal)) {
            spec = spec.and(ProjectSpecifications.memberOf(principal.getId()));
        }

        Page<Project> page = projectRepository.findAll(spec, pageable);
        return PagedResponse.from(page, ProjectResponse::from);
    }

    private void validateDates(LocalDate start, LocalDate expectedCompletion) {
        if (expectedCompletion.isBefore(start)) {
            throw new BusinessRuleException("INVALID_PROJECT_DATES",
                    "expectedCompletionDate must not be before startDate");
        }
    }

    private void checkVersion(Project project, Long submittedVersion) {
        if (submittedVersion != null && !Objects.equals(submittedVersion, project.getVersion())) {
            throw new StaleResourceException("Project", submittedVersion, project.getVersion());
        }
    }
}
