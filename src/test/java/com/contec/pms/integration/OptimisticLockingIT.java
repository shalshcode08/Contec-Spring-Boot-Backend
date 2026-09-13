package com.contec.pms.integration;

import com.contec.pms.domain.entity.Project;
import com.contec.pms.domain.entity.Task;
import com.contec.pms.domain.entity.User;
import com.contec.pms.domain.enums.ProjectMemberRole;
import com.contec.pms.domain.enums.TaskPriority;
import com.contec.pms.domain.enums.TaskStatus;
import com.contec.pms.support.AbstractIntegrationTest;
import com.contec.pms.web.dto.request.UpdateProgressRequest;
import com.contec.pms.web.dto.request.UpdateTaskRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OptimisticLockingIT extends AbstractIntegrationTest {

    private User manager;
    private User engineer;
    private Project project;
    private Task task;

    @BeforeEach
    void setUpTask() {
        manager = createManager("pm@contec.com");
        engineer = createEngineer("eng@contec.com");
        project = createProject("Riverside Tower", manager);
        addMember(project, manager, ProjectMemberRole.MANAGER);
        addMember(project, engineer, ProjectMemberRole.ENGINEER);
        task = createTask(project, manager, engineer, TaskStatus.IN_PROGRESS, 20);
    }

    @Test
    void staleProgressUpdateIsRejectedAndTheNewerChangeSurvives() throws Exception {
        long staleVersion = task.getVersion();

        mockMvc.perform(patch("/api/tasks/" + task.getId() + "/progress")
                        .header(HttpHeaders.AUTHORIZATION, bearer(engineer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateProgressRequest(55, "first writer", staleVersion))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress").value(55));

        mockMvc.perform(patch("/api/tasks/" + task.getId() + "/progress")
                        .header(HttpHeaders.AUTHORIZATION, bearer(engineer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateProgressRequest(30, "second writer", staleVersion))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_RESOURCE"));

        assertThat(taskRepository.findById(task.getId()).orElseThrow().getProgress()).isEqualTo(55);
    }

    @Test
    void staleTaskUpdateIsRejected() throws Exception {
        UpdateTaskRequest first = new UpdateTaskRequest("Renamed by first writer", "desc",
                TaskPriority.HIGH, null, task.getVersion());

        mockMvc.perform(put("/api/tasks/" + task.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(first)))
                .andExpect(status().isOk());

        UpdateTaskRequest second = new UpdateTaskRequest("Renamed by second writer", "desc",
                TaskPriority.LOW, null, task.getVersion());

        mockMvc.perform(put("/api/tasks/" + task.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(second)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_RESOURCE"));

        assertThat(taskRepository.findById(task.getId()).orElseThrow().getTitle())
                .isEqualTo("Renamed by first writer");
    }

    @Test
    void versionIsExposedAndIncrementsAfterAWrite() throws Exception {
        long before = task.getVersion();

        mockMvc.perform(patch("/api/tasks/" + task.getId() + "/progress")
                        .header(HttpHeaders.AUTHORIZATION, bearer(engineer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateProgressRequest(45, null, before))))
                .andExpect(status().isOk());

        assertThat(taskRepository.findById(task.getId()).orElseThrow().getVersion()).isGreaterThan(before);
    }
}
