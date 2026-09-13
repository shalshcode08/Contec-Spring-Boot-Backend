-- demo profile only; every demo user has the password Password@123
SET @pwd = '$2y$10$XqnLkDhljuNkQOYoLXpMauUZhgBAW6KI9zSaHY9ImmUBqaxotix6u';

INSERT IGNORE INTO users (email, password_hash, full_name, active, created_at, updated_at) VALUES
    ('alex.pm@contec.com',    @pwd, 'Alex Rivera',  TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('jordan.pm@contec.com',  @pwd, 'Jordan Blake', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('sam.eng@contec.com',    @pwd, 'Sam Ortiz',    TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('riley.eng@contec.com',  @pwd, 'Riley Chen',   TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('taylor.eng@contec.com', @pwd, 'Taylor Novak', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
         JOIN roles r ON r.name = 'PROJECT_MANAGER'
WHERE u.email IN ('alex.pm@contec.com', 'jordan.pm@contec.com');

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
         JOIN roles r ON r.name = 'SITE_ENGINEER'
WHERE u.email IN ('sam.eng@contec.com', 'riley.eng@contec.com', 'taylor.eng@contec.com');

INSERT INTO projects (name, description, location, start_date, expected_completion_date,
                      status, created_by, created_at, updated_at, version)
SELECT 'Riverside Tower', 'Twenty-storey mixed-use tower on the east bank.', 'Riverside, Block C',
       '2026-01-15', '2027-06-30', 'ACTIVE', u.id, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0
FROM users u
WHERE u.email = 'admin@contec.com'
  AND NOT EXISTS (SELECT 1 FROM projects p WHERE p.name = 'Riverside Tower');

INSERT INTO projects (name, description, location, start_date, expected_completion_date,
                      status, created_by, created_at, updated_at, version)
SELECT 'Metro Depot Expansion', 'Additional maintenance bays and a new signalling room.', 'North Metro Depot',
       '2026-03-01', '2026-12-20', 'ACTIVE', u.id, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0
FROM users u
WHERE u.email = 'admin@contec.com'
  AND NOT EXISTS (SELECT 1 FROM projects p WHERE p.name = 'Metro Depot Expansion');

INSERT IGNORE INTO project_members (project_id, user_id, project_role, added_by, added_at)
SELECT p.id, u.id, 'MANAGER', a.id, UTC_TIMESTAMP(6)
FROM projects p
         JOIN users u ON u.email = 'alex.pm@contec.com'
         JOIN users a ON a.email = 'admin@contec.com'
WHERE p.name = 'Riverside Tower';

INSERT IGNORE INTO project_members (project_id, user_id, project_role, added_by, added_at)
SELECT p.id, u.id, 'ENGINEER', a.id, UTC_TIMESTAMP(6)
FROM projects p
         JOIN users u ON u.email IN ('sam.eng@contec.com', 'riley.eng@contec.com')
         JOIN users a ON a.email = 'admin@contec.com'
WHERE p.name = 'Riverside Tower';

INSERT IGNORE INTO project_members (project_id, user_id, project_role, added_by, added_at)
SELECT p.id, u.id, 'MANAGER', a.id, UTC_TIMESTAMP(6)
FROM projects p
         JOIN users u ON u.email = 'jordan.pm@contec.com'
         JOIN users a ON a.email = 'admin@contec.com'
WHERE p.name = 'Metro Depot Expansion';

INSERT IGNORE INTO project_members (project_id, user_id, project_role, added_by, added_at)
SELECT p.id, u.id, 'ENGINEER', a.id, UTC_TIMESTAMP(6)
FROM projects p
         JOIN users u ON u.email = 'taylor.eng@contec.com'
         JOIN users a ON a.email = 'admin@contec.com'
WHERE p.name = 'Metro Depot Expansion';

INSERT INTO tasks (project_id, title, description, assignee_id, status, priority, progress,
                   expected_completion_date, created_by, created_at, updated_at, version)
SELECT p.id, 'Excavate foundation grid A1-A8', 'Excavate and shore the northern foundation grid.',
       e.id, 'IN_PROGRESS', 'HIGH', 40, '2026-04-30', m.id, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0
FROM projects p
         JOIN users e ON e.email = 'sam.eng@contec.com'
         JOIN users m ON m.email = 'alex.pm@contec.com'
WHERE p.name = 'Riverside Tower'
  AND NOT EXISTS (SELECT 1 FROM tasks t WHERE t.project_id = p.id AND t.title = 'Excavate foundation grid A1-A8');

INSERT INTO tasks (project_id, title, description, assignee_id, status, priority, progress,
                   expected_completion_date, created_by, created_at, updated_at, version)
SELECT p.id, 'Install tower crane base', 'Pour and cure the crane base slab.',
       e.id, 'TODO', 'CRITICAL', 0, '2026-05-15', m.id, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0
FROM projects p
         JOIN users e ON e.email = 'riley.eng@contec.com'
         JOIN users m ON m.email = 'alex.pm@contec.com'
WHERE p.name = 'Riverside Tower'
  AND NOT EXISTS (SELECT 1 FROM tasks t WHERE t.project_id = p.id AND t.title = 'Install tower crane base');

INSERT INTO tasks (project_id, title, description, assignee_id, status, priority, progress,
                   expected_completion_date, created_by, created_at, updated_at, version)
SELECT p.id, 'Survey existing bay drainage', 'Record drainage falls before demolition.',
       e.id, 'TODO', 'MEDIUM', 0, '2026-04-10', m.id, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0
FROM projects p
         JOIN users e ON e.email = 'taylor.eng@contec.com'
         JOIN users m ON m.email = 'jordan.pm@contec.com'
WHERE p.name = 'Metro Depot Expansion'
  AND NOT EXISTS (SELECT 1 FROM tasks t WHERE t.project_id = p.id AND t.title = 'Survey existing bay drainage');

INSERT INTO task_activities (task_id, actor_id, activity_type, old_status, new_status,
                             old_progress, new_progress, detail, created_at)
SELECT t.id, t.created_by, 'TASK_CREATED', NULL, 'TODO', NULL, 0, 'Seeded demo task', UTC_TIMESTAMP(6)
FROM tasks t
WHERE NOT EXISTS (SELECT 1 FROM task_activities a WHERE a.task_id = t.id);
