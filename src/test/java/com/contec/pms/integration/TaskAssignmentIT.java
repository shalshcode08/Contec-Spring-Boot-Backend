package com.contec.pms.integration;

import com.contec.pms.domain.entity.Project;
import com.contec.pms.domain.entity.Task;
import com.contec.pms.domain.entity.User;
import com.contec.pms.domain.enums.ProjectMemberRole;
import com.contec.pms.domain.enums.TaskStatus;
import com.contec.pms.support.AbstractIntegrationTest;
import com.contec.pms.web.dto.request.AssignTaskRequest;
import com.contec.pms.web.dto.request.CreateTaskRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskAssignmentIT extends AbstractIntegrationTest {

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
    void managerAssignsATaskToAProjectEngineer() throws Exception {
        Task task = createTask(project, manager, null, TaskStatus.TODO, 0);

        mockMvc.perform(post("/api/tasks/" + task.getId() + "/assign")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AssignTaskRequest(engineer.getId(), task.getVersion()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee.email").value("eng@contec.com"));
    }

    @Test
    void cannotAssignToAnEngineerOutsideTheProject() throws Exception {
        User outsider = createEngineer("outsider@contec.com");
        Task task = createTask(project, manager, null, TaskStatus.TODO, 0);

        mockMvc.perform(post("/api/tasks/" + task.getId() + "/assign")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AssignTaskRequest(outsider.getId(), task.getVersion()))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ASSIGNEE_NOT_A_MEMBER"));
    }

    @Test
    void cannotAssignToSomeoneWhoIsNotASiteEngineer() throws Exception {
        User otherManager = createManager("pm2@contec.com");
        addMember(project, otherManager, ProjectMemberRole.MANAGER);
        Task task = createTask(project, manager, null, TaskStatus.TODO, 0);

        mockMvc.perform(post("/api/tasks/" + task.getId() + "/assign")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AssignTaskRequest(otherManager.getId(), task.getVersion()))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_ASSIGNEE_ROLE"));
    }

    @Test
    void engineerCannotAssignTasks() throws Exception {
        Task task = createTask(project, manager, null, TaskStatus.TODO, 0);

        mockMvc.perform(post("/api/tasks/" + task.getId() + "/assign")
                        .header(HttpHeaders.AUTHORIZATION, bearer(engineer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AssignTaskRequest(engineer.getId(), task.getVersion()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void engineerCannotCreateTasks() throws Exception {
        mockMvc.perform(post("/api/projects/" + project.getId() + "/tasks")
                        .header(HttpHeaders.AUTHORIZATION, bearer(engineer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateTaskRequest("Sneaky task", null, null, null, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotReassignAnApprovedTask() throws Exception {
        Task task = createTask(project, manager, engineer, TaskStatus.APPROVED, 100);
        User another = createEngineer("eng2@contec.com");
        addMember(project, another, ProjectMemberRole.ENGINEER);

        mockMvc.perform(post("/api/tasks/" + task.getId() + "/assign")
                        .header(HttpHeaders.AUTHORIZATION, bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AssignTaskRequest(another.getId(), task.getVersion()))))
                .andExpect(status().isConflict());
    }

    @Test
    void unassignedTaskCannotBeStartedByAnyone() throws Exception {
        Task task = createTask(project, manager, null, TaskStatus.TODO, 0);

        mockMvc.perform(post("/api/tasks/" + task.getId() + "/start")
                        .header(HttpHeaders.AUTHORIZATION, bearer(engineer)))
                .andExpect(status().isForbidden());
    }
}
