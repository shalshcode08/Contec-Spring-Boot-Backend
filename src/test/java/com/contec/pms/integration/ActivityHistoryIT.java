package com.contec.pms.integration;

import com.contec.pms.domain.entity.Project;
import com.contec.pms.domain.entity.TaskActivity;
import com.contec.pms.domain.entity.User;
import com.contec.pms.domain.enums.ActivityType;
import com.contec.pms.domain.enums.TaskStatus;
import com.contec.pms.support.AbstractIntegrationTest;
import com.contec.pms.web.dto.request.CreateTaskRequest;
import com.contec.pms.web.dto.request.UpdateProgressRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActivityHistoryIT extends AbstractIntegrationTest {

    private User manager;
    private User engineer;
    private Project project;

    @BeforeEach
    void setUpProject() {
        manager = createManager("pm@contec.com");
        engineer = createEngineer("eng@contec.com");
        project = createProject("Riverside Tower", manager);
        addMember(project, manager);
        addMember(project, engineer);
    }

    @Test
    void everyImportantActionIsRecorded() throws Exception {
        String created = mockMvc.perform(post("/api/projects/" + project.getId() + "/tasks")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateTaskRequest("Excavate grid A1", "desc", null, null,
                                engineer.getId()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long taskId = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(post("/api/tasks/" + taskId + "/start")
                .header(HttpHeaders.AUTHORIZATION, bearer(engineer))).andExpect(status().isOk());

        mockMvc.perform(patch("/api/tasks/" + taskId + "/progress")
                        .header(HttpHeaders.AUTHORIZATION, bearer(engineer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateProgressRequest(70, "on track", versionOf(taskId)))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/tasks/" + taskId + "/complete")
                .header(HttpHeaders.AUTHORIZATION, bearer(engineer))).andExpect(status().isOk());

        mockMvc.perform(post("/api/tasks/" + taskId + "/approve")
                .header(HttpHeaders.AUTHORIZATION, bearer(manager))).andExpect(status().isOk());

        List<TaskActivity> activities = taskActivityRepository.findByTaskIdOrderByCreatedAtAscIdAsc(taskId);
        assertThat(activities).extracting(TaskActivity::getActivityType)
                .containsExactly(
                        ActivityType.TASK_CREATED,
                        ActivityType.TASK_ASSIGNED,
                        ActivityType.TASK_STARTED,
                        ActivityType.PROGRESS_UPDATED,
                        ActivityType.TASK_COMPLETED,
                        ActivityType.TASK_APPROVED);

        assertThat(activities).allSatisfy(activity -> {
            assertThat(activity.getActor()).isNotNull();
            assertThat(activity.getCreatedAt()).isNotNull();
        });

        TaskActivity progressUpdate = activities.get(3);
        assertThat(progressUpdate.getActor().getEmail()).isEqualTo("eng@contec.com");
        assertThat(progressUpdate.getOldProgress()).isZero();
        assertThat(progressUpdate.getNewProgress()).isEqualTo(70);

        TaskActivity approval = activities.get(5);
        assertThat(approval.getActor().getEmail()).isEqualTo("pm@contec.com");
        assertThat(approval.getNewStatus()).isEqualTo(TaskStatus.APPROVED);
    }

    @Test
    void activityEndpointReturnsHistoryNewestFirst() throws Exception {
        String created = mockMvc.perform(post("/api/projects/" + project.getId() + "/tasks")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateTaskRequest("Install crane base", null, null, null,
                                engineer.getId()))))
                .andReturn().getResponse().getContentAsString();
        long taskId = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(post("/api/tasks/" + taskId + "/start")
                .header(HttpHeaders.AUTHORIZATION, bearer(engineer))).andExpect(status().isOk());

        mockMvc.perform(get("/api/tasks/" + taskId + "/activities")
                        .header(HttpHeaders.AUTHORIZATION, bearer(engineer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].activityType").value("TASK_STARTED"))
                .andExpect(jsonPath("$.content[0].actor.email").value("eng@contec.com"))
                .andExpect(jsonPath("$.content[0].createdAt").isNotEmpty());
    }

    @Test
    void outsiderCannotReadActivityHistory() throws Exception {
        User outsider = createEngineer("outsider@contec.com");
        long taskId = createTask(project, manager, engineer, TaskStatus.TODO, 0).getId();

        mockMvc.perform(get("/api/tasks/" + taskId + "/activities")
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isForbidden());
    }
}
