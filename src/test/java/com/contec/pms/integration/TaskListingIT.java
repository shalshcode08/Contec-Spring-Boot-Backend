package com.contec.pms.integration;

import com.contec.pms.domain.entity.Project;
import com.contec.pms.domain.entity.Task;
import com.contec.pms.domain.entity.User;
import com.contec.pms.domain.enums.TaskPriority;
import com.contec.pms.domain.enums.TaskStatus;
import com.contec.pms.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskListingIT extends AbstractIntegrationTest {

    private User manager;
    private User engineerOne;
    private User engineerTwo;
    private Project project;

    @BeforeEach
    void setUpTasks() {
        manager = createManager("pm@contec.com");
        engineerOne = createEngineer("eng1@contec.com");
        engineerTwo = createEngineer("eng2@contec.com");
        project = createProject("Riverside Tower", manager);
        addMember(project, manager);
        addMember(project, engineerOne);
        addMember(project, engineerTwo);

        withPriority(createTask(project, manager, engineerOne, TaskStatus.TODO, 0), TaskPriority.LOW);
        withPriority(createTask(project, manager, engineerOne, TaskStatus.IN_PROGRESS, 30), TaskPriority.HIGH);
        withPriority(createTask(project, manager, engineerTwo, TaskStatus.IN_PROGRESS, 60), TaskPriority.HIGH);
        withPriority(createTask(project, manager, engineerTwo, TaskStatus.COMPLETED, 100), TaskPriority.CRITICAL);
        withPriority(createTask(project, manager, null, TaskStatus.TODO, 0), TaskPriority.MEDIUM);
    }

    @Test
    void listsEveryTaskOfTheProject() throws Exception {
        mockMvc.perform(get("/api/projects/" + project.getId() + "/tasks")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content", hasSize(5)));
    }

    @Test
    void paginates() throws Exception {
        mockMvc.perform(get("/api/projects/" + project.getId() + "/tasks")
                        .param("page", "1")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.first").value(false));
    }

    @Test
    void sorts() throws Exception {
        mockMvc.perform(get("/api/projects/" + project.getId() + "/tasks")
                        .param("sort", "progress,desc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].progress").value(100))
                .andExpect(jsonPath("$.content[4].progress").value(0));
    }

    @Test
    void filtersByStatus() throws Exception {
        mockMvc.perform(get("/api/projects/" + project.getId() + "/tasks")
                        .param("status", "IN_PROGRESS")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void filtersByAssignee() throws Exception {
        mockMvc.perform(get("/api/projects/" + project.getId() + "/tasks")
                        .param("assigneeId", String.valueOf(engineerOne.getId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void filtersByPriority() throws Exception {
        mockMvc.perform(get("/api/projects/" + project.getId() + "/tasks")
                        .param("priority", "HIGH")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void combinesFilters() throws Exception {
        mockMvc.perform(get("/api/projects/" + project.getId() + "/tasks")
                        .param("priority", "HIGH")
                        .param("assigneeId", String.valueOf(engineerTwo.getId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].assignee.email").value("eng2@contec.com"));
    }

    @Test
    void rejectsAnUnknownStatusValue() throws Exception {
        mockMvc.perform(get("/api/projects/" + project.getId() + "/tasks")
                        .param("status", "NOT_A_STATUS")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void engineersSeeTheProjectTaskBoardToo() throws Exception {
        mockMvc.perform(get("/api/projects/" + project.getId() + "/tasks")
                        .header(HttpHeaders.AUTHORIZATION, bearer(engineerOne)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5));
    }

    private void withPriority(Task task, TaskPriority priority) {
        task.setPriority(priority);
        taskRepository.save(task);
    }
}
