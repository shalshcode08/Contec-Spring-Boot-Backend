# Contec Project Management Backend

Backend module for managing construction projects, tasks and approvals.

Java 17 · Spring Boot 3.3 · Spring Data JPA · Spring Security (JWT) · MySQL · Flyway · JUnit 5 · springdoc OpenAPI

> New here? [**GETTING_STARTED.md**](GETTING_STARTED.md) is a step-by-step guide to installing,
> running and using the API, with copy-paste commands and a troubleshooting section.

---

## Setup

### 1. Database

MySQL 8 is required. Create the application and test schemas:

```bash
mysql -u root -p -e "CREATE DATABASE contec_pms CHARACTER SET utf8mb4; \
                     CREATE DATABASE contec_pms_test CHARACTER SET utf8mb4;"
```

Connection settings live in `src/main/resources/application.yml` and default to
`jdbc:mysql://localhost:3306/contec_pms` with user `root` and an empty password.
Override with environment variables instead of editing the file:

```bash
export DB_URL="jdbc:mysql://localhost:3306/contec_pms?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
export DB_USERNAME=root
export DB_PASSWORD=secret
export APP_JWT_SECRET=<at least 32 characters>
```

Flyway creates and migrates the schema on startup; no manual DDL is needed.

### 2. Build

```bash
./mvnw clean package
```

The Maven Wrapper downloads Maven 3.9.9 on first use, so no global Maven install is required.

### 3. Run

```bash
./mvnw spring-boot:run
```

Sample data (two managers, three engineers, two projects, tasks) is available under the `demo` profile:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

The API listens on `http://localhost:8080`.

### 4. Tests

Tests run against the **`contec_pms_test`** schema on the same MySQL server, so MySQL must be
running first. Flyway migrates that schema automatically and each test starts from a clean slate.

```bash
./mvnw test
```

Point them elsewhere with `TEST_DB_URL`, `TEST_DB_USERNAME` and `TEST_DB_PASSWORD`.

### 5. API documentation

* Swagger UI — <http://localhost:8080/swagger-ui.html>
* OpenAPI JSON — <http://localhost:8080/v3/api-docs>
* Postman collection — [`docs/contec-pms.postman_collection.json`](docs/contec-pms.postman_collection.json)

In Swagger UI, call `POST /api/auth/login`, copy `accessToken`, click **Authorize** and paste it.

### Accounts

| Account | Password | Where it comes from |
| --- | --- | --- |
| `admin@contec.com` | `Admin@123` | migration `V2`, always present |
| `alex.pm@contec.com`, `jordan.pm@contec.com` | `Password@123` | `demo` profile |
| `sam.eng@contec.com`, `riley.eng@contec.com`, `taylor.eng@contec.com` | `Password@123` | `demo` profile |

Change the administrator password before any real deployment.

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
| POST / GET / DELETE | `/api/projects/{id}/members[/{userId}]` | ADMIN, the project's manager (GET: members) |
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
GET /api/projects/1/tasks?status=IN_PROGRESS&assigneeId=4&priority=HIGH&page=0&size=20&sort=priority,desc
```

### Quick walkthrough

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@contec.com","password":"Admin@123"}' | jq -r .accessToken)

curl -s localhost:8080/api/projects -H "Authorization: Bearer $TOKEN"
```

---

## Architecture

### Application structure

```
com.contec.pms
├── config      security, OpenAPI, JPA auditing, JWT properties
├── security    JWT issuing/parsing, authentication filter, 401/403 handlers
├── domain      entities and enums (TaskStatus owns the workflow rules)
├── repository  Spring Data repositories and Specifications for filtering
├── service     business logic and authorization decisions
├── web         controllers, request/response DTOs, error handling
└── common      auditing base class
```

Controllers stay thin: they bind and validate the request, then hand off to a service.
Services own transactions, authorization and domain rules, and return DTOs — entities never
leave the transaction boundary.

### Database design

```
roles ──< user_roles >── users
                           │
                           ├──< project_members >── projects
                           │                           │
                           └──< tasks (assignee) >─────┘
                                     │
                                     └──< task_activities (actor)
```

* **users / roles / user_roles** — a user may hold several global roles.
* **projects** — name, description, location, start and expected completion dates, status,
  creator, timestamps, `version`.
* **project_members** — unique `(project_id, user_id)` with a per-project role
  (`MANAGER` / `ENGINEER`). This table, not the global role, decides project authority.
* **tasks** — title, description, assignee, status, priority, progress, expected completion date,
  completion/approval/rejection details, timestamps and a `version` column for optimistic locking.
* **task_activities** — append-only audit trail: actor, activity type, old/new status,
  old/new progress, free-text detail and a timestamp.

Schema and seed data are managed by Flyway (`src/main/resources/db/migration`). Hibernate runs with
`ddl-auto: validate`, so the entities are checked against the migrated schema at startup.

### Authentication

`POST /api/auth/login` verifies the credentials through Spring Security's `AuthenticationManager`
and returns a signed HS256 JWT (subject = user id, plus email and roles, one hour by default).
Passwords are stored as BCrypt hashes. `JwtAuthenticationFilter` validates the
`Authorization: Bearer …` header on every request and loads the user, so a deactivated or deleted
account stops working immediately. Sessions are stateless and CSRF is disabled.
Failures are rendered as the standard error body: 401 from the authentication entry point,
403 from the access-denied handler.

### Authorization

Enforced by the backend at two levels:

1. **Role gates** — `@PreAuthorize` on endpoints that are role-specific (only ADMIN creates users;
   only ADMIN or PROJECT_MANAGER creates projects).
2. **Project-scoped checks** — `AccessControlService` is the single place that answers whether a
   user may read or manage a given project. Global roles alone grant nothing:

   * ADMIN — everything.
   * PROJECT_MANAGER — only projects where they are a member with `MANAGER`: update the project,
     manage members, create/update/assign tasks, approve and reject.
   * SITE_ENGINEER — only projects they belong to: read the project and its tasks; start, report
     progress on and complete **tasks assigned to them**.

   A site engineer cannot approve (403), cannot touch another engineer's task (403), and cannot see
   a project they are not assigned to (403) — `GET /api/projects` is filtered to their memberships.

### Transactions

Every mutating service method is `@Transactional`; reads are `@Transactional(readOnly = true)`.
Activity writes go through `TaskActivityService`, whose methods are
`@Transactional(propagation = MANDATORY)` — they can only run inside a caller's transaction, so a
status change and its activity record always commit or roll back together. `TransactionRollbackIT`
proves this by making the activity write fail during completion and asserting the task is untouched.

### Concurrency

Tasks and projects carry a JPA `@Version` column. Every response exposes `version`, and every
mutating task request carries it back. The service compares the submitted version with the stored
one and answers `409 STALE_RESOURCE` when they differ, so a client working from an older copy can
never silently overwrite a newer change. Hibernate's own version check at flush time catches true
simultaneous writes and maps to the same 409.

```
409 {"code":"STALE_RESOURCE","message":"Task has been modified by someone else
     (submitted version 2, current version 3). Reload it and try again."}
```

### Validation and errors

Request validation uses Bean Validation (required fields, valid email, password length,
`progress` between 0 and 100, rejection reason mandatory). Business rules live in the services
(project dates, assignment rules, status transitions, progress/status consistency).

All failures are rendered by `GlobalExceptionHandler` in one shape:

```json
{
  "timestamp": "2026-09-13T12:00:00Z",
  "status": 409,
  "error": "Conflict",
  "code": "INVALID_STATUS_TRANSITION",
  "message": "Cannot move a task from TODO to COMPLETED. Allowed from TODO: [IN_PROGRESS]",
  "path": "/api/tasks/7/complete"
}
```

| Status | When |
| --- | --- |
| 400 `VALIDATION_FAILED` / `MALFORMED_REQUEST` | invalid body, bad enum or missing parameter |
| 401 `UNAUTHENTICATED` | missing, invalid or expired token; bad credentials |
| 403 `ACCESS_DENIED` | authenticated but not allowed |
| 404 `RESOURCE_NOT_FOUND` | unknown project, task or user |
| 409 `INVALID_STATUS_TRANSITION` / `STALE_RESOURCE` | illegal workflow move or outdated version |
| 422 business codes | rule violations such as `ASSIGNEE_NOT_A_MEMBER`, `INVALID_PROJECT_DATES` |

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
* Reporting progress above 0 on a `TODO` or `REJECTED` task starts it (`IN_PROGRESS`) and records
  `TASK_STARTED`.
* Completing forces `progress = 100` and stamps `completedAt`.
* `COMPLETED` and `APPROVED` tasks refuse progress edits (409).
* Rejecting requires a reason, clears `completedAt` and sends the task back for rework.

Recorded activities: `TASK_CREATED`, `TASK_ASSIGNED`, `TASK_UPDATED`, `TASK_STARTED`,
`PROGRESS_UPDATED`, `TASK_COMPLETED`, `TASK_APPROVED`, `TASK_REJECTED` — each with actor and
timestamp, readable through `GET /api/tasks/{id}/activities`.

---

## Tests

| Test | Covers |
| --- | --- |
| `TaskStatusTransitionTest` | every valid and invalid status transition |
| `AuthenticationIT` | valid and invalid login, protected routes with and without a token |
| `ProjectAccessIT` | cross-project access, membership-scoped listing, unrelated manager |
| `TaskWorkflowIT` | full lifecycle end to end plus rejected transitions |
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

* A user may hold several global roles; the seed data gives each user one.
* Project authority comes from `project_members`, not the global role — a project manager who is
  not a member of a project has no access to it at all.
* Administrators bypass project membership. They are also the "explicitly permitted" exception that
  may act on a task assigned to someone else.
* A project manager who creates a project is added as its `MANAGER`; an administrator can nominate
  one with `managerId` or add members afterwards.
* Tasks may only be assigned to active users holding `SITE_ENGINEER` who are members of the project.
* `APPROVED` is terminal. A rejected task returns to `IN_PROGRESS` for rework and may be completed
  and approved again.
* Completing a task is an explicit action: reaching 100 % progress does not complete it by itself.
* Optimistic locking is expressed as a `version` field in request and response bodies rather than
  `If-Match`/`ETag` headers, which keeps it visible in Swagger UI.
* Members with unfinished tasks cannot be removed from a project until their tasks are reassigned.
* Users are created by administrators; there is no public self-registration.
* Tests share the MySQL server with the application but use a separate schema.
