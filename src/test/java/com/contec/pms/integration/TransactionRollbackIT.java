package com.contec.pms.integration;

import com.contec.pms.domain.entity.Project;
import com.contec.pms.domain.entity.Task;
import com.contec.pms.domain.entity.User;
import com.contec.pms.domain.enums.TaskStatus;
import com.contec.pms.exception.InvalidStatusTransitionException;
import com.contec.pms.repository.TaskActivityRepository;
import com.contec.pms.security.AppUserDetails;
import com.contec.pms.service.TaskService;
import com.contec.pms.support.AbstractIntegrationTest;
import com.contec.pms.web.dto.request.RejectTaskRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

class TransactionRollbackIT extends AbstractIntegrationTest {

    @SpyBean
    private TaskActivityRepository activityRepositorySpy;

    @Autowired
    private TaskService taskService;

    private User manager;
    private User engineer;
    private Task task;

    @BeforeEach
    void setUpTask() {
        manager = createManager("pm@contec.com");
        engineer = createEngineer("eng@contec.com");
        Project project = createProject("Riverside Tower", manager);
        addMember(project, manager);
        addMember(project, engineer);
        task = createTask(project, manager, engineer, TaskStatus.IN_PROGRESS, 40);
    }

    @Test
    void aFailedActivityWriteRollsBackTheStatusChange() {
        doThrow(new DataIntegrityViolationException("activity write failed"))
                .when(activityRepositorySpy).save(any());

        AppUserDetails principal = principalFor(engineer);

        assertThatThrownBy(() -> taskService.complete(principal, task.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        Task reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(reloaded.getProgress()).isEqualTo(40);
        assertThat(reloaded.getCompletedAt()).isNull();
        assertThat(taskActivityRepository.countByTaskId(task.getId())).isZero();
    }

    @Test
    void aRejectedBusinessRuleLeavesNoActivityBehind() {
        AppUserDetails principal = principalFor(manager);
        RejectTaskRequest request = new RejectTaskRequest("not good enough");

        assertThatThrownBy(() -> taskService.reject(principal, task.getId(), request))
                .isInstanceOf(InvalidStatusTransitionException.class);

        Task reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(reloaded.getRejectionReason()).isNull();
        assertThat(taskActivityRepository.countByTaskId(task.getId())).isZero();
    }

    private AppUserDetails principalFor(User user) {
        return new AppUserDetails(userRepository.findById(user.getId()).orElseThrow());
    }
}
