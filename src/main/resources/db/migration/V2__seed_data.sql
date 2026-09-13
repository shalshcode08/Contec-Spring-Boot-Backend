-- admin password: Admin@123 | everyone else: Password@123 (BCrypt) - change before any real use
INSERT INTO users (email, password_hash, full_name, role, active, created_at, updated_at) VALUES
    ('admin@contec.com',      '$2y$10$rgDBc2iZMKO/ULXrjw2wZOHVu5slz08JulBdCjhruWvx/nL0Ui2Yy', 'Contec Administrator', 'ADMIN',           TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('alex.pm@contec.com',    '$2y$10$XqnLkDhljuNkQOYoLXpMauUZhgBAW6KI9zSaHY9ImmUBqaxotix6u', 'Alex Rivera',          'PROJECT_MANAGER', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('jordan.pm@contec.com',  '$2y$10$XqnLkDhljuNkQOYoLXpMauUZhgBAW6KI9zSaHY9ImmUBqaxotix6u', 'Jordan Blake',         'PROJECT_MANAGER', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('sam.eng@contec.com',    '$2y$10$XqnLkDhljuNkQOYoLXpMauUZhgBAW6KI9zSaHY9ImmUBqaxotix6u', 'Sam Ortiz',            'SITE_ENGINEER',   TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('riley.eng@contec.com',  '$2y$10$XqnLkDhljuNkQOYoLXpMauUZhgBAW6KI9zSaHY9ImmUBqaxotix6u', 'Riley Chen',           'SITE_ENGINEER',   TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('taylor.eng@contec.com', '$2y$10$XqnLkDhljuNkQOYoLXpMauUZhgBAW6KI9zSaHY9ImmUBqaxotix6u', 'Taylor Novak',         'SITE_ENGINEER',   TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT INTO projects (name, description, location, start_date, expected_completion_date, status, created_by, created_at, updated_at)
SELECT 'Riverside Tower', 'Twenty-storey mixed-use tower on the east bank.', 'Riverside, Block C',
       '2026-01-15', '2027-06-30', 'ACTIVE', id, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM users WHERE email = 'admin@contec.com';

INSERT INTO projects (name, description, location, start_date, expected_completion_date, status, created_by, created_at, updated_at)
SELECT 'Metro Depot Expansion', 'Additional maintenance bays and a new signalling room.', 'North Metro Depot',
       '2026-03-01', '2026-12-20', 'ACTIVE', id, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM users WHERE email = 'admin@contec.com';

-- Alex manages Riverside Tower with Sam and Riley; Jordan manages Metro Depot with Taylor
INSERT INTO project_members (project_id, user_id, added_at)
SELECT p.id, u.id, UTC_TIMESTAMP(6)
FROM projects p JOIN users u ON u.email IN ('alex.pm@contec.com', 'sam.eng@contec.com', 'riley.eng@contec.com')
WHERE p.name = 'Riverside Tower';

INSERT INTO project_members (project_id, user_id, added_at)
SELECT p.id, u.id, UTC_TIMESTAMP(6)
FROM projects p JOIN users u ON u.email IN ('jordan.pm@contec.com', 'taylor.eng@contec.com')
WHERE p.name = 'Metro Depot Expansion';

INSERT INTO tasks (project_id, title, description, assignee_id, status, priority, progress,
                   expected_completion_date, created_by, created_at, updated_at, version)
SELECT p.id, 'Excavate foundation grid A1-A8', 'Excavate and shore the northern foundation grid.',
       e.id, 'IN_PROGRESS', 'HIGH', 40, '2026-04-30', m.id, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0
FROM projects p
         JOIN users e ON e.email = 'sam.eng@contec.com'
         JOIN users m ON m.email = 'alex.pm@contec.com'
WHERE p.name = 'Riverside Tower';

INSERT INTO tasks (project_id, title, description, assignee_id, status, priority, progress,
                   expected_completion_date, created_by, created_at, updated_at, version)
SELECT p.id, 'Install tower crane base', 'Pour and cure the crane base slab.',
       e.id, 'TODO', 'CRITICAL', 0, '2026-05-15', m.id, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0
FROM projects p
         JOIN users e ON e.email = 'riley.eng@contec.com'
         JOIN users m ON m.email = 'alex.pm@contec.com'
WHERE p.name = 'Riverside Tower';

INSERT INTO tasks (project_id, title, description, assignee_id, status, priority, progress,
                   expected_completion_date, created_by, created_at, updated_at, version)
SELECT p.id, 'Survey existing bay drainage', 'Record drainage falls before demolition.',
       e.id, 'TODO', 'MEDIUM', 0, '2026-04-10', m.id, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0
FROM projects p
         JOIN users e ON e.email = 'taylor.eng@contec.com'
         JOIN users m ON m.email = 'jordan.pm@contec.com'
WHERE p.name = 'Metro Depot Expansion';

INSERT INTO task_activities (task_id, actor_id, activity_type, new_status, new_progress, detail, created_at)
SELECT t.id, t.created_by, 'TASK_CREATED', t.status, t.progress, 'Seeded sample task', UTC_TIMESTAMP(6)
FROM tasks t;
