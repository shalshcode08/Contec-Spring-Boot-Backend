# Contec Project Management Backend

Backend module for managing construction projects, tasks and approvals.

Java 17 · Spring Boot 3.3 · Spring Data JPA · Spring Security (JWT) · MySQL · Flyway · JUnit 5 · springdoc OpenAPI

> New here? [**GETTING_STARTED.md**](GETTING_STARTED.md) is a step-by-step guide for Windows, macOS
> and Linux, with copy-paste commands and troubleshooting.

---

## Setup

### 1. Database

MySQL 8 is required. Create the application and test schemas:

```bash
mysql -u root -p -e "CREATE DATABASE contec_pms CHARACTER SET utf8mb4; CREATE DATABASE contec_pms_test CHARACTER SET utf8mb4;"
```

Defaults are `jdbc:mysql://localhost:3306/contec_pms`, user `root`, empty password. Override with
environment variables rather than editing files:

```
DB_URL  DB_USERNAME  DB_PASSWORD  APP_JWT_SECRET
TEST_DB_URL  TEST_DB_USERNAME  TEST_DB_PASSWORD
```

Flyway creates and migrates the schema on startup; there is no manual DDL.

### 2. Build, run, test

The Maven Wrapper downloads Maven on first use, so no global Maven install is needed.
Use `./mvnw` on macOS/Linux and `.\mvnw.cmd` on Windows.

```bash
./mvnw clean package      # build
./mvnw spring-boot:run    # run on http://localhost:8080
./mvnw test               # tests (needs MySQL running and contec_pms_test to exist)
```

### 3. API documentation

* Swagger UI — <http://localhost:8080/swagger-ui.html>
* OpenAPI JSON — <http://localhost:8080/v3/api-docs>
* Postman collection — [`docs/contec-pms.postman_collection.json`](docs/contec-pms.postman_collection.json)

Call `POST /api/auth/login`, copy `accessToken`, click **Authorize** in Swagger and paste it.

### Accounts

Created by migration `V2`:

| Email | Password | Role |
| --- | --- | --- |
| `admin@contec.com` | `Admin@123` | ADMIN |
| `alex.pm@contec.com`, `jordan.pm@contec.com` | `Password@123` | PROJECT_MANAGER |
| `sam.eng@contec.com`, `riley.eng@contec.com`, `taylor.eng@contec.com` | `Password@123` | SITE_ENGINEER |

Alex manages *Riverside Tower* with Sam and Riley; Jordan manages *Metro Depot Expansion* with Taylor.
Change the admin password before any real deployment.

---

## API

| Method | Path | Who may call it |
| --- | --- | --- |
| POST | `/api/auth/login` | anyone |
| GET | `/api/auth/me` | any authenticated user |
| POST | `/api/users` | ADMIN |
| GET | `/api/users`, `/api/users/{id}` | ADMIN, PROJECT_MANAGER |
| POST | `/api/projects` | ADMIN, PROJECT_MANAGER |
| GET | `/api/projects` | any user — scoped to their projects |
| GET | `/api/projects/{id}` | project members |
| PUT | `/api/projects/{id}` | ADMIN, the project's manager |
| POST / GET | `/api/projects/{id}/members` | manager to add, members to read |
| POST | `/api/projects/{id}/tasks` | ADMIN, the project's manager |
| GET | `/api/projects/{id}/tasks` | project members |
| GET | `/api/tasks/{id}` | project members |
| PUT | `/api/tasks/{id}` | ADMIN, the project's manager |
| POST | `/api/tasks/{id}/assign` | ADMIN, the project's manager |
| POST | `/api/tasks/{id}/start` | the assigned engineer |
| PATCH | `/api/tasks/{id}/progress` | the assigned engineer |
| POST | `/api/tasks/{id}/complete` | the assigned engineer |
| POST | `/api/tasks/{id}/approve` | ADMIN, the project's manager |
| POST | `/api/tasks/{id}/reject` | ADMIN, the project's manager |
| GET | `/api/tasks/{id}/activities` | project members |

Task listing supports paging, sorting and filtering:

```
GET /api/projects/1/tasks?status=IN_PROGRESS&assigneeId=4&priority=HIGH&page=0&size=20&sort=createdAt,desc
```

---

## Architecture

### Application structure

```
com.contec.pms
├── config      security, OpenAPI, JPA auditing
├── security    JWT issuing/parsing, authentication filter, 401/403 handlers
├── domain      entities and enums (TaskStatus owns the workflow rules)
├── repository  Spring Data repositories
├── service     business logic and authorization decisions
├── web         controllers, request/response DTOs, error handling
└── common      auditing base class
```

Controllers bind and validate the request, then hand off to a service. Services own transactions,
authorization and domain rules, and return DTOs — entities never leave the transaction boundary.

### Database design

```
users ──< project_members >── projects
  │                              │
  └──< tasks (assignee) >────────┘
            │
            └──< task_activities (actor)
```

* **users** — email, BCrypt password hash, full name, `role` (`ADMIN` / `PROJECT_MANAGER` /
  `SITE_ENGINEER`), active flag, timestamps.
* **projects** — name, description, location, start and expected completion dates, status, creator,
  timestamps.
* **project_members** — unique `(project_id, user_id)`. This table decides who can reach a project.
* **tasks** — title, description, assignee, status, priority, progress, expected completion date,
  completion/approval/rejection details, timestamps, and a `version` column for optimistic locking.
* **task_activities** — append-only audit trail: actor, activity type, old/new status, old/new
  progress, detail, timestamp.

Schema and seed data are managed by Flyway (`src/main/resources/db/migration`). Hibernate runs with
`ddl-auto: validate`, so entities are checked against the migrated schema at startup.

### Authentication

`POST /api/auth/login` verifies credentials through Spring Security's `AuthenticationManager` and
returns a signed HS256 JWT (subject = email, plus user id and role, one hour by default). Passwords
are BCrypt hashes. `JwtAuthenticationFilter` validates the `Authorization: Bearer …` header on every
request and loads the user, so a deactivated account stops working immediately. Sessions are
stateless and CSRF is disabled. Failures use the standard error body: 401 from the authentication
entry point, 403 from the access-denied handler.

### Authorization

Two levels, both enforced by the backend:

1. **Role gates** — `@PreAuthorize` on role-specific endpoints (only ADMIN creates users; only ADMIN
   or PROJECT_MANAGER creates projects).
2. **Project scope** — `AccessControlService` is the single place that decides whether a user may
   read or manage a given project:
   * ADMIN — everything.
   * PROJECT_MANAGER — only projects they are a member of: update the project, add members,
     create/update/assign tasks, approve and reject.
   * SITE_ENGINEER — only projects they are a member of: read the project and its tasks; start,
     report progress on and complete **tasks assigned to them**.

A site engineer cannot approve (403), cannot touch another engineer's task (403), and cannot see a
project they are not assigned to (403) — `GET /api/projects` is filtered to their memberships.

### Transactions

Every mutating service method is `@Transactional`; reads are `@Transactional(readOnly = true)`. The
task change and its activity record are written in the same transaction, so they commit or roll back
together. `TransactionRollbackIT` proves it by making the activity write fail during completion and
asserting the task is untouched.

### Concurrency

Tasks carry a JPA `@Version` column, exposed as `version` in every task response. The three
endpoints that change task content — update, assign and progress — require the client to send it
back. If it does not match the stored value the request is rejected with `409 STALE_RESOURCE`
instead of silently overwriting newer changes. Hibernate's own version check at flush time catches
true simultaneous writes and maps to the same 409.

### Validation and errors

Bean Validation covers the request (required fields, valid email, password length, progress between
0 and 100, rejection reason mandatory). Business rules live in the services (project dates,
assignment rules, status transitions, progress/status consistency).

`GlobalExceptionHandler` renders every failure in one shape:

```json
{
  "timestamp": "2026-09-14T12:00:00Z",
  "status": 409,
  "error": "Conflict",
  "code": "INVALID_STATUS_TRANSITION",
  "message": "Cannot move a task from TODO to COMPLETED. Allowed from TODO: [IN_PROGRESS]",
  "path": "/api/tasks/7/complete"
}
```

| Status | When |
| --- | --- |
| 400 `VALIDATION_FAILED` / `MALFORMED_REQUEST` | invalid body, bad enum, missing parameter |
| 401 `UNAUTHENTICATED` | missing, invalid or expired token; bad credentials |
| 403 `ACCESS_DENIED` | authenticated but not allowed |
| 404 `RESOURCE_NOT_FOUND` | unknown project, task or user |
| 409 `INVALID_STATUS_TRANSITION` / `STALE_RESOURCE` | illegal workflow move or outdated version |
| 422 business codes | `ASSIGNEE_NOT_A_MEMBER`, `INVALID_PROJECT_DATES`, … |

---

## Task workflow

```
TODO ──▶ IN_PROGRESS ──▶ COMPLETED ──▶ APPROVED   (terminal)
            ▲                    └────▶ REJECTED
            └────────────────────────────┘  (rework)
```

Invalid moves are refused with 409. Rules tying status to progress:

* `progress` is always between 0 and 100.
* Progress may only change while the task is `TODO`, `IN_PROGRESS` or `REJECTED`.
* Reporting progress above 0 on a `TODO` or `REJECTED` task starts it and records `TASK_STARTED`.
* Completing forces `progress = 100` and stamps `completedAt`.
* `COMPLETED` and `APPROVED` tasks refuse progress edits (409).
* Rejecting requires a reason, clears `completedAt` and sends the task back for rework.

Activities recorded: `TASK_CREATED`, `TASK_ASSIGNED`, `TASK_UPDATED`, `TASK_STARTED`,
`PROGRESS_UPDATED`, `TASK_COMPLETED`, `TASK_APPROVED`, `TASK_REJECTED` — each with actor and
timestamp, readable through `GET /api/tasks/{id}/activities`.

---

## Tests

| Test | Covers |
| --- | --- |
| `TaskStatusTransitionTest` | every valid and invalid status transition |
| `AuthenticationIT` | valid and invalid login, protected routes |
| `ProjectAccessIT` | cross-project access, membership-scoped listing, unrelated manager |
| `TaskWorkflowIT` | full lifecycle end to end plus refused transitions |
| `TaskProgressIT` | valid and invalid progress values, wrong engineer, completed task |
| `TaskApprovalIT` | approval, rejection with reason, engineer attempting approval |
| `TaskAssignmentIT` | assignment rules — membership and role |
| `ActivityHistoryIT` | activity records for every important action, history endpoint |
| `TransactionRollbackIT` | a failed activity write leaves no partial update |
| `OptimisticLockingIT` | stale updates rejected, newer change preserved |
| `TaskListingIT` | pagination, sorting, filtering by status, assignee and priority |

```bash
./mvnw test
```

---

## Assumptions

* A user has exactly one role. The three roles in the brief are enough for this domain, so the role
  is a column on `users` rather than a separate roles table.
* Project access comes from `project_members`, not from the role alone — a project manager who is
  not a member of a project has no access to it. Any project manager on a project can manage it.
* Administrators bypass membership. They are also the "explicitly permitted" exception that may act
  on a task assigned to someone else.
* A project manager who creates a project joins it automatically; an administrator can nominate one
  with `managerId` or add members afterwards.
* Tasks may only be assigned to active `SITE_ENGINEER` users who are members of the project.
* `APPROVED` is terminal. A rejected task returns to `IN_PROGRESS` for rework and can be completed
  and approved again.
* Completing a task is an explicit action: reaching 100 % progress does not complete it by itself.
* Optimistic locking is expressed as a `version` field in request and response bodies rather than
  `If-Match`/`ETag` headers, which keeps it visible in Swagger UI. It is required only on the
  endpoints that change task content.
* Users are created by administrators; there is no public self-registration.
* Sample data ships in the baseline migration so the API is usable immediately.
* Tests share the MySQL server with the application but use a separate schema.
