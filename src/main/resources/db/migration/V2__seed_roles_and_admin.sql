-- ---------------------------------------------------------------------------
-- Reference roles and the bootstrap administrator.
--
-- The administrator password is 'Admin@123' (BCrypt, strength 10). It exists so
-- the API is usable immediately after migration; change it before any real use.
-- ---------------------------------------------------------------------------

INSERT INTO roles (name) VALUES ('ADMIN'), ('PROJECT_MANAGER'), ('SITE_ENGINEER');

INSERT INTO users (email, password_hash, full_name, active, created_at, updated_at)
VALUES ('admin@contec.com',
        '$2y$10$rgDBc2iZMKO/ULXrjw2wZOHVu5slz08JulBdCjhruWvx/nL0Ui2Yy',
        'Contec Administrator',
        TRUE,
        UTC_TIMESTAMP(6),
        UTC_TIMESTAMP(6));

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
         JOIN roles r ON r.name = 'ADMIN'
WHERE u.email = 'admin@contec.com';
