package com.contec.pms.integration;

import com.contec.pms.domain.entity.Project;
import com.contec.pms.domain.entity.Task;
import com.contec.pms.domain.entity.User;
import com.contec.pms.domain.enums.ProjectMemberRole;
import com.contec.pms.domain.enums.TaskStatus;
import com.contec.pms.support.AbstractIntegrationTest;
import com.contec.pms.web.dto.request.ApproveTaskRequest;
import com.contec.pms.web.dto.request.CompleteTaskRequest;
import com.contec.pms.web.dto.request.CreateTaskRequest;
import com.contec.pms.web.dto.request.UpdateProgressRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskWorkflowIT extends AbstractIntegrationTest {

    private User manager;
    private User engineer;
    private Project project;

    @BeforeEach
    void setUpProject() {
        manager = createManager("pm@contec.com");
        engineer = createEngineer("eng@contec.com");
        project = createProject("Riverside Tower", manager);
        addMember(project, manager, ProjectMemberRole.MANAGER);
        addMember(project, engineer, ProjectMemberRole.ENGINEER);
    }

    @Test
    void walksTheFullLifecycle() throws Exception {
        String created = mockMvc.perform(post("/api/projects/" + project.getId() + "/tasks")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateTaskRequest("Excavate grid A1", "desc", null, null,
                                engineer.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.progress").value(0))
                .andReturn().getResponse().getContentAsString();

        long taskId = objectMapper.readTree(created).get("id").asLong();
        long version = objectMapper.readTree(created).get("version").asLong();

        mockMvc.perform(post("/api/tasks/" + taskId + "/start")
                        .header(HttpHeaders.AUTHORIZATION, bearer(engineer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        version = currentVersion(taskId);
        mockMvc.perform(patch("/api/tasks/" + taskId + "/progress")
                        .header(HttpHeaders.AUTHORIZATION, bearer(engineer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateProgressRequest(60, "framework up", version))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress").value(60));

        version = currentVersion(taskId);
        mockMvc.perform(post("/api/tasks/" + taskId + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(engineer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CompleteTaskRequest("done", version))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.progress").value(100))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());

        version = currentVersion(taskId);
        mockMvc.perform(post("/api/tasks/" + taskId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ApproveTaskRequest("looks good", version))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedBy.email").value("pm@contec.com"));

        assertThat(taskRepository.findById(taskId).orElseThrow().getStatus())
                .isEqualTo(TaskStatus.APPROVED);
    }

    @Test
    void cannotCompleteATaskThatWasNeverStarted() throws Exception {
        Task task = createTask(project, manager, engineer, TaskStatus.TODO, 0);

        mockMvc.perform(post("/api/tasks/" + task.getId() + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(engineer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CompleteTaskRequest(null, task.getVersion()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void cannotApproveATaskThatIsNotCompleted() throws Exception {
        Task task = createTask(project, manager, engineer, TaskStatus.IN_PROGRESS, 30);

        mockMvc.perform(post("/api/tasks/" + task.getId() + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ApproveTaskRequest(null, task.getVersion()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void cannotRestartAnApprovedTask() throws Exception {
        Task task = createTask(project, manager, engineer, TaskStatus.APPROVED, 100);

        mockMvc.perform(post("/api/tasks/" + task.getId() + "/start")
                        .header(HttpHeaders.AUTHORIZATION, bearer(engineer)))
                .andExpect(status().isConflict());
    }

    @Test
    void cannotStartATaskThatIsAlreadyInProgress() throws Exception {
        Task task = createTask(project, manager, engineer, TaskStatus.IN_PROGRESS, 20);

        mockMvc.perform(post("/api/tasks/" + task.getId() + "/start")
                        .header(HttpHeaders.AUTHORIZATION, bearer(engineer)))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectedTaskCanBeRestartedAndCompletedAgain() throws Exception {
        Task task = createTask(project, manager, engineer, TaskStatus.REJECTED, 100);

        mockMvc.perform(post("/api/tasks/" + task.getId() + "/start")
                        .header(HttpHeaders.AUTHORIZATION, bearer(engineer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(post("/api/tasks/" + task.getId() + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(engineer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CompleteTaskRequest("reworked", currentVersion(task.getId())))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    private long currentVersion(long taskId) {
        return taskRepository.findById(taskId).orElseThrow().getVersion();
    }
}
