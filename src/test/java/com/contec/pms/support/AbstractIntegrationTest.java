package com.contec.pms.support;

import com.contec.pms.domain.entity.Project;
import com.contec.pms.domain.entity.ProjectMember;
import com.contec.pms.domain.entity.Role;
import com.contec.pms.domain.entity.Task;
import com.contec.pms.domain.entity.User;
import com.contec.pms.domain.enums.ProjectMemberRole;
import com.contec.pms.domain.enums.ProjectStatus;
import com.contec.pms.domain.enums.RoleName;
import com.contec.pms.domain.enums.TaskPriority;
import com.contec.pms.domain.enums.TaskStatus;
import com.contec.pms.repository.ProjectMemberRepository;
import com.contec.pms.repository.ProjectRepository;
import com.contec.pms.repository.RoleRepository;
import com.contec.pms.repository.TaskActivityRepository;
import com.contec.pms.repository.TaskRepository;
import com.contec.pms.repository.UserRepository;
import com.contec.pms.security.AppUserDetails;
import com.contec.pms.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Set;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    public static final String PASSWORD = "Password@123";

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected JdbcTemplate jdbcTemplate;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected RoleRepository roleRepository;
    @Autowired
    protected ProjectRepository projectRepository;
    @Autowired
    protected ProjectMemberRepository projectMemberRepository;
    @Autowired
    protected TaskRepository taskRepository;
    @Autowired
    protected TaskActivityRepository taskActivityRepository;
    @Autowired
    protected PasswordEncoder passwordEncoder;
    @Autowired
    protected JwtService jwtService;

    // tests are not transactional, so each one starts from a clean slate
    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbcTemplate.execute("DELETE FROM task_activities");
        jdbcTemplate.execute("DELETE FROM tasks");
        jdbcTemplate.execute("DELETE FROM project_members");
        jdbcTemplate.execute("DELETE FROM projects");
        jdbcTemplate.execute("DELETE FROM user_roles");
        jdbcTemplate.execute("DELETE FROM users");
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
    }

    protected User createUser(String email, String fullName, RoleName... roles) {
        Set<Role> roleEntities = new java.util.LinkedHashSet<>();
        for (RoleName role : roles) {
            roleEntities.add(roleRepository.findByName(role).orElseThrow());
        }
        User user = new User();
        user.setEmail(email);
        user.setFullName(fullName);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setActive(true);
        user.setRoles(roleEntities);
        return userRepository.save(user);
    }

    protected User createAdmin(String email) {
        return createUser(email, "Admin " + email, RoleName.ADMIN);
    }

    protected User createManager(String email) {
        return createUser(email, "Manager " + email, RoleName.PROJECT_MANAGER);
    }

    protected User createEngineer(String email) {
        return createUser(email, "Engineer " + email, RoleName.SITE_ENGINEER);
    }

    protected Project createProject(String name, User creator) {
        Project project = new Project();
        project.setName(name);
        project.setDescription("Test project");
        project.setLocation("Test site");
        project.setStartDate(LocalDate.now());
        project.setExpectedCompletionDate(LocalDate.now().plusMonths(6));
        project.setStatus(ProjectStatus.ACTIVE);
        project.setCreatedBy(creator);
        return projectRepository.save(project);
    }

    protected ProjectMember addMember(Project project, User user, ProjectMemberRole role) {
        return projectMemberRepository.save(new ProjectMember(project, user, role, user));
    }

    protected Task createTask(Project project, User creator, User assignee, TaskStatus status, int progress) {
        Task task = new Task();
        task.setProject(project);
        task.setTitle("Pour slab " + System.nanoTime());
        task.setDescription("Test task");
        task.setPriority(TaskPriority.MEDIUM);
        task.setStatus(status);
        task.setProgress(progress);
        task.setExpectedCompletionDate(LocalDate.now().plusMonths(1));
        task.setCreatedBy(creator);
        task.setAssignee(assignee);
        return taskRepository.save(task);
    }

    protected String tokenFor(User user) {
        return jwtService.generateToken(new AppUserDetails(userRepository.findById(user.getId()).orElseThrow()));
    }

    protected String bearer(User user) {
        return "Bearer " + tokenFor(user);
    }

    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}
