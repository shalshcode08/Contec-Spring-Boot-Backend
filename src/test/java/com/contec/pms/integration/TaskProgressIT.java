package com.contec.pms.integration;

import com.contec.pms.domain.entity.Project;
import com.contec.pms.domain.entity.Task;
import com.contec.pms.domain.entity.User;
import com.contec.pms.domain.enums.TaskStatus;
import com.contec.pms.support.AbstractIntegrationTest;
import com.contec.pms.web.dto.request.UpdateProgressRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskProgressIT extends AbstractIntegrationTest {

    private User manager;
    private User engineer;
    private User otherEngineer;
    private Project project;

    @BeforeEach
    void setUpProject() {
        manager = createManager("pm@contec.com");
        engineer = createEngineer("eng@contec.com");
        otherEngineer = createEngineer("eng2@contec.com");
        project = createProject("Riverside Tower", manager);
        addMember(project, manager);
        addMember(project, engineer);
        addMember(project, otherEngineer);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 50, 100})
    void acceptsProgressWithinRange(int progress) throws Exception {
        Task task = createTask(project, manager, engineer, TaskStatus.IN_PROGRESS, 10);

        mockMvc.perform(patch("/api/tasks/" + task.getId() + "/progress")
                        .header(HttpHeaders.AUTHORIZATION, bearer(engineer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateProgressRequest(progress, null, task.getVersion()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress").value(progress));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 101, 1000})
    void rejectsProgressOutsideRange(int progress) throws Exception {
        Task task = createTask(project, manager, engineer, TaskStatus.IN_PROGRESS, 10);

        mockMvc.perform(patch("/api/tasks/" + task.getId() + "/progress")
                        .header(HttpHeaders.AUTHORIZATION, bearer(engineer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateProgressRequest(progress, null, task.getVersion()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("progress"));
    }

    @Test
    void reportingProgressOnATodoTaskStartsIt() throws Exception {
        Task task = createTask(project, manager, engineer, TaskStatus.TODO, 0);

        mockMvc.perform(patch("/api/tasks/" + task.getId() + "/progress")
                        .header(HttpHeaders.AUTHORIZATION, bearer(engineer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateProgressRequest(25, "started digging", task.getVersion()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.progress").value(25));
    }

    @Test
    void progressCannotChangeOnACompletedTask() throws Exception {
        Task task = createTask(project, manager, engineer, TaskStatus.COMPLETED, 100);

        mockMvc.perform(patch("/api/tasks/" + task.getId() + "/progress")
                        .header(HttpHeaders.AUTHORIZATION, bearer(engineer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateProgressRequest(80, null, task.getVersion()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void engineerCannotUpdateAnotherEngineersTask() throws Exception {
        Task task = createTask(project, manager, engineer, TaskStatus.IN_PROGRESS, 10);

        mockMvc.perform(patch("/api/tasks/" + task.getId() + "/progress")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherEngineer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateProgressRequest(40, null, task.getVersion()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void engineerOutsideTheProjectCannotUpdateProgress() throws Exception {
        User outsider = createEngineer("outsider@contec.com");
        Task task = createTask(project, manager, engineer, TaskStatus.IN_PROGRESS, 10);

        mockMvc.perform(patch("/api/tasks/" + task.getId() + "/progress")
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateProgressRequest(40, null, task.getVersion()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void versionIsRequired() throws Exception {
        Task task = createTask(project, manager, engineer, TaskStatus.IN_PROGRESS, 10);

        mockMvc.perform(patch("/api/tasks/" + task.getId() + "/progress")
                        .header(HttpHeaders.AUTHORIZATION, bearer(engineer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateProgressRequest(40, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
