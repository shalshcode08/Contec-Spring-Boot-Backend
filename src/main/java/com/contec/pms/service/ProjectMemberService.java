package com.contec.pms.service;

import com.contec.pms.domain.entity.Project;
import com.contec.pms.domain.entity.ProjectMember;
import com.contec.pms.domain.entity.User;
import com.contec.pms.domain.enums.ProjectMemberRole;
import com.contec.pms.domain.enums.RoleName;
import com.contec.pms.domain.enums.TaskStatus;
import com.contec.pms.exception.BusinessRuleException;
import com.contec.pms.exception.ResourceNotFoundException;
import com.contec.pms.repository.ProjectMemberRepository;
import com.contec.pms.repository.TaskRepository;
import com.contec.pms.repository.UserRepository;
import com.contec.pms.security.AppUserDetails;
import com.contec.pms.web.dto.request.AddProjectMemberRequest;
import com.contec.pms.web.dto.response.ProjectMemberResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ProjectMemberService {

    private static final EnumSet<TaskStatus> OPEN_STATUSES =
            EnumSet.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.COMPLETED, TaskStatus.REJECTED);

    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final AccessControlService accessControl;

    public ProjectMemberService(ProjectMemberRepository projectMemberRepository,
                                UserRepository userRepository,
                                TaskRepository taskRepository,
                                AccessControlService accessControl) {
        this.projectMemberRepository = projectMemberRepository;
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.accessControl = accessControl;
    }

    @Transactional
    public ProjectMemberResponse addMember(AppUserDetails principal, Long projectId,
                                           AddProjectMemberRequest request) {
        Project project = accessControl.requireProjectManagement(principal, projectId);

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.userId()));
        if (!user.isActive()) {
            throw new BusinessRuleException("INACTIVE_USER",
                    "User " + user.getId() + " is not active");
        }
        requireMatchingGlobalRole(user, request.projectRole());

        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, user.getId())) {
            throw new BusinessRuleException("ALREADY_A_MEMBER",
                    "User " + user.getId() + " is already a member of project " + projectId);
        }

        User addedBy = userRepository.getReferenceById(principal.getId());
        ProjectMember member = projectMemberRepository.save(
                new ProjectMember(project, user, request.projectRole(), addedBy));
        return ProjectMemberResponse.from(member);
    }

    public List<ProjectMemberResponse> listMembers(AppUserDetails principal, Long projectId) {
        accessControl.requireProjectAccess(principal, projectId);
        return projectMemberRepository.findByProjectIdOrderByAddedAtAsc(projectId).stream()
                .map(ProjectMemberResponse::from)
                .toList();
    }

    @Transactional
    public void removeMember(AppUserDetails principal, Long projectId, Long userId) {
        accessControl.requireProjectManagement(principal, projectId);

        ProjectMember member = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Membership of user " + userId + " in project " + projectId));

        long openTasks = taskRepository.countByProjectIdAndAssigneeIdAndStatusIn(
                projectId, userId, OPEN_STATUSES);
        if (openTasks > 0) {
            throw new BusinessRuleException("MEMBER_HAS_OPEN_TASKS",
                    "User " + userId + " still has " + openTasks
                            + " unfinished task(s) on this project; reassign them first");
        }

        projectMemberRepository.delete(member);
    }

    // project authority may never exceed the user's global role
    private void requireMatchingGlobalRole(User user, ProjectMemberRole projectRole) {
        RoleName required = projectRole == ProjectMemberRole.MANAGER
                ? RoleName.PROJECT_MANAGER
                : RoleName.SITE_ENGINEER;
        if (!user.hasRole(required) && !user.hasRole(RoleName.ADMIN)) {
            throw new BusinessRuleException("ROLE_MISMATCH",
                    "User " + user.getId() + " must have the " + required
                            + " role to join a project as " + projectRole);
        }
    }
}
