-- ---------------------------------------------------------------------------
-- Contec PMS baseline schema
-- ---------------------------------------------------------------------------

CREATE TABLE roles (
    id   BIGINT      NOT NULL AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uk_roles_name UNIQUE (name)
) ENGINE = InnoDB;

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(180) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(150) NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE = InnoDB;

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE = InnoDB;

CREATE TABLE projects (
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    name                     VARCHAR(200) NOT NULL,
    description              TEXT         NULL,
    location                 VARCHAR(255) NULL,
    start_date               DATE         NOT NULL,
    expected_completion_date DATE         NOT NULL,
    status                   VARCHAR(30)  NOT NULL,
    created_by               BIGINT       NOT NULL,
    created_at               DATETIME(6)  NOT NULL,
    updated_at               DATETIME(6)  NOT NULL,
    version                  BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_projects PRIMARY KEY (id),
    CONSTRAINT fk_projects_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_projects_dates CHECK (expected_completion_date >= start_date)
) ENGINE = InnoDB;

CREATE INDEX ix_projects_status ON projects (status);

-- Project membership is what scopes every authorization decision: a user can only
-- reach a project they are a member of, and only manage one where they are MANAGER.
CREATE TABLE project_members (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    project_id   BIGINT      NOT NULL,
    user_id      BIGINT      NOT NULL,
    project_role VARCHAR(30) NOT NULL,
    added_by     BIGINT      NULL,
    added_at     DATETIME(6) NOT NULL,
    CONSTRAINT pk_project_members PRIMARY KEY (id),
    CONSTRAINT uk_project_members_project_user UNIQUE (project_id, user_id),
    CONSTRAINT fk_project_members_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_members_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_project_members_added_by FOREIGN KEY (added_by) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE INDEX ix_project_members_user ON project_members (user_id);

CREATE TABLE tasks (
    id                       BIGINT        NOT NULL AUTO_INCREMENT,
    project_id               BIGINT        NOT NULL,
    title                    VARCHAR(200)  NOT NULL,
    description              TEXT          NULL,
    assignee_id              BIGINT        NULL,
    status                   VARCHAR(30)   NOT NULL,
    priority                 VARCHAR(30)   NOT NULL,
    progress                 INT           NOT NULL DEFAULT 0,
    expected_completion_date DATE          NULL,
    completed_at             DATETIME(6)   NULL,
    approved_by              BIGINT        NULL,
    approved_at              DATETIME(6)   NULL,
    rejected_by              BIGINT        NULL,
    rejected_at              DATETIME(6)   NULL,
    rejection_reason         VARCHAR(1000) NULL,
    created_by               BIGINT        NOT NULL,
    created_at               DATETIME(6)   NOT NULL,
    updated_at               DATETIME(6)   NOT NULL,
    version                  BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_tasks PRIMARY KEY (id),
    CONSTRAINT fk_tasks_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_tasks_assignee FOREIGN KEY (assignee_id) REFERENCES users (id),
    CONSTRAINT fk_tasks_approved_by FOREIGN KEY (approved_by) REFERENCES users (id),
    CONSTRAINT fk_tasks_rejected_by FOREIGN KEY (rejected_by) REFERENCES users (id),
    CONSTRAINT fk_tasks_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_tasks_progress CHECK (progress BETWEEN 0 AND 100)
) ENGINE = InnoDB;

CREATE INDEX ix_tasks_project_status ON tasks (project_id, status);
CREATE INDEX ix_tasks_project_priority ON tasks (project_id, priority);
CREATE INDEX ix_tasks_assignee ON tasks (assignee_id);

CREATE TABLE task_activities (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    task_id       BIGINT        NOT NULL,
    actor_id      BIGINT        NOT NULL,
    activity_type VARCHAR(40)   NOT NULL,
    old_status    VARCHAR(30)   NULL,
    new_status    VARCHAR(30)   NULL,
    old_progress  INT           NULL,
    new_progress  INT           NULL,
    detail        VARCHAR(1000) NULL,
    created_at    DATETIME(6)   NOT NULL,
    CONSTRAINT pk_task_activities PRIMARY KEY (id),
    CONSTRAINT fk_task_activities_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_task_activities_actor FOREIGN KEY (actor_id) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE INDEX ix_task_activities_task_created ON task_activities (task_id, created_at);
