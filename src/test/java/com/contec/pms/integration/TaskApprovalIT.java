package com.contec.pms.integration;

import com.contec.pms.domain.entity.Project;
import com.contec.pms.domain.entity.Task;
import com.contec.pms.domain.entity.User;
import com.contec.pms.domain.enums.TaskStatus;
import com.contec.pms.support.AbstractIntegrationTest;
import com.contec.pms.web.dto.request.RejectTaskRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskApprovalIT extends AbstractIntegrationTest {

    private User manager;
    private User engineer;
    private Project project;
    private Task completedTask;

    @BeforeEach
    void setUpCompletedTask() {
        manager = createManager("pm@contec.com");
        engineer = createEngineer("eng@contec.com");
        project = createProject("Riverside Tower", manager);
        addMember(project, manager);
        addMember(project, engineer);
        completedTask = createTask(project, manager, engineer, TaskStatus.COMPLETED, 100);
    }

    @Test
    void managerApprovesACompletedTask() throws Exception {
        mockMvc.perform(post("/api/tasks/" + completedTask.getId() + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedAt").isNotEmpty())
                .andExpect(jsonPath("$.approvedBy.email").value("pm@contec.com"));
    }

    @Test
    void managerRejectsACompletedTaskWithAReason() throws Exception {
        mockMvc.perform(post("/api/tasks/" + completedTask.getId() + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RejectTaskRequest("Rebar spacing is wrong"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Rebar spacing is wrong"))
                .andExpect(jsonPath("$.rejectedBy.email").value("pm@contec.com"));

        assertThat(taskRepository.findById(completedTask.getId()).orElseThrow().getCompletedAt()).isNull();
    }

    @Test
    void rejectionWithoutAReasonIsRejected() throws Exception {
        mockMvc.perform(post("/api/tasks/" + completedTask.getId() + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RejectTaskRequest("   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("reason"));
    }

    @Test
    void siteEngineerCannotApprove() throws Exception {
        mockMvc.perform(post("/api/tasks/" + completedTask.getId() + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(engineer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(taskRepository.findById(completedTask.getId()).orElseThrow().getStatus())
                .isEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    void siteEngineerCannotReject() throws Exception {
        mockMvc.perform(post("/api/tasks/" + completedTask.getId() + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, bearer(engineer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RejectTaskRequest("nope"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerOfAnotherProjectCannotApprove() throws Exception {
        User otherManager = createManager("pm2@contec.com");
        Project otherProject = createProject("Metro Depot", otherManager);
        addMember(otherProject, otherManager);

        mockMvc.perform(post("/api/tasks/" + completedTask.getId() + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherManager)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anApprovedTaskCannotBeRejectedAfterwards() throws Exception {
        Task approved = createTask(project, manager, engineer, TaskStatus.APPROVED, 100);

        mockMvc.perform(post("/api/tasks/" + approved.getId() + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RejectTaskRequest("too late"))))
                .andExpect(status().isConflict());
    }
}
