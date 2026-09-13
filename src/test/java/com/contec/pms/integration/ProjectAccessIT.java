package com.contec.pms.integration;

import com.contec.pms.domain.entity.Project;
import com.contec.pms.domain.entity.User;
import com.contec.pms.domain.enums.ProjectStatus;
import com.contec.pms.domain.enums.TaskStatus;
import com.contec.pms.support.AbstractIntegrationTest;
import com.contec.pms.web.dto.request.AddProjectMemberRequest;
import com.contec.pms.web.dto.request.CreateProjectRequest;
import com.contec.pms.web.dto.request.UpdateProjectRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectAccessIT extends AbstractIntegrationTest {

    private User managerA;
    private User managerB;
    private User engineerA;
    private Project projectA;
    private Project projectB;

    @BeforeEach
    void setUpProjects() {
        managerA = createManager("managerA@contec.com");
        managerB = createManager("managerB@contec.com");
        engineerA = createEngineer("engineerA@contec.com");

        projectA = createProject("Project A", managerA);
        projectB = createProject("Project B", managerB);

        addMember(projectA, managerA);
        addMember(projectA, engineerA);
        addMember(projectB, managerB);
    }

    @Test
    void engineerCanReadTheirOwnProject() throws Exception {
        mockMvc.perform(get("/api/projects/" + projectA.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(engineerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Project A"));
    }

    @Test
    void engineerCannotReadAnotherProject() throws Exception {
        mockMvc.perform(get("/api/projects/" + projectB.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(engineerA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void engineerCannotListTasksOfAnotherProject() throws Exception {
        createTask(projectB, managerB, null, TaskStatus.TODO, 0);

        mockMvc.perform(get("/api/projects/" + projectB.getId() + "/tasks")
                        .header(HttpHeaders.AUTHORIZATION, bearer(engineerA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void projectListingIsScopedToMemberships() throws Exception {
        mockMvc.perform(get("/api/projects").header(HttpHeaders.AUTHORIZATION, bearer(engineerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Project A"));
    }

    @Test
    void administratorSeesEveryProject() throws Exception {
        User admin = createAdmin("admin@contec.com");

        mockMvc.perform(get("/api/projects").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void managerCannotUpdateAnUnrelatedProject() throws Exception {
        UpdateProjectRequest request = new UpdateProjectRequest("Hijacked", "x", "y",
                LocalDate.now(), LocalDate.now().plusMonths(2), ProjectStatus.ACTIVE);

        mockMvc.perform(put("/api/projects/" + projectB.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerCanUpdateTheirOwnProject() throws Exception {
        UpdateProjectRequest request = new UpdateProjectRequest("Project A renamed", "x", "y",
                LocalDate.now(), LocalDate.now().plusMonths(2), ProjectStatus.ON_HOLD);

        mockMvc.perform(put("/api/projects/" + projectA.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Project A renamed"))
                .andExpect(jsonPath("$.status").value("ON_HOLD"));
    }

    @Test
    void engineerCannotAddMembers() throws Exception {
        User other = createEngineer("other@contec.com");

        mockMvc.perform(post("/api/projects/" + projectA.getId() + "/members")
                        .header(HttpHeaders.AUTHORIZATION, bearer(engineerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AddProjectMemberRequest(other.getId()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void engineerCannotCreateProjects() throws Exception {
        CreateProjectRequest request = new CreateProjectRequest("New site", null, null,
                LocalDate.now(), LocalDate.now().plusMonths(3), ProjectStatus.PLANNED, null);

        mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(engineerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerCreatingAProjectBecomesAMember() throws Exception {
        CreateProjectRequest request = new CreateProjectRequest("Depot C", "desc", "site",
                LocalDate.now(), LocalDate.now().plusMonths(3), ProjectStatus.PLANNED, null);

        String response = mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long projectId = objectMapper.readTree(response).get("id").asLong();
        mockMvc.perform(get("/api/projects/" + projectId + "/members")
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("managerA@contec.com"))
                .andExpect(jsonPath("$[0].role").value("PROJECT_MANAGER"));
    }

    @Test
    void projectDatesAreValidated() throws Exception {
        CreateProjectRequest request = new CreateProjectRequest("Bad dates", null, null,
                LocalDate.now(), LocalDate.now().minusDays(1), ProjectStatus.PLANNED, null);

        mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_PROJECT_DATES"));
    }
}
