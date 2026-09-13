# Getting Started

A complete, step-by-step guide to running this project and using its API.
No prior knowledge of the codebase is needed. Every command can be copied and pasted.

---

## Contents

1. [What you need](#1-what-you-need)
2. [Quick start](#2-quick-start)
3. [Step by step setup](#3-step-by-step-setup)
4. [Logging in](#4-logging-in)
5. [Using the API: a full walkthrough](#5-using-the-api-a-full-walkthrough)
6. [Who is allowed to do what](#6-who-is-allowed-to-do-what)
7. [Understanding `version`](#7-understanding-version)
8. [Running the tests](#8-running-the-tests)
9. [Stopping and resetting](#9-stopping-and-resetting)
10. [Troubleshooting](#10-troubleshooting)
11. [Endpoint cheat sheet](#11-endpoint-cheat-sheet)

---

## 1. What you need

| Tool | Version | Check it with | Install it with |
| --- | --- | --- | --- |
| Java | 17 or newer | `java -version` | `brew install openjdk@17` |
| MySQL | 8.0 or newer | `mysql --version` | `brew install mysql` |
| Maven | not needed | — | the project ships `./mvnw`, which downloads Maven for you |

You also need an internet connection the **first** time you build, because `./mvnw`
downloads Maven and the project's libraries into `~/.m2`. Nothing is installed system-wide.

Optional but handy: [`jq`](https://jqlang.github.io/jq/) (`brew install jq`) to read JSON responses in the terminal.

---

## 2. Quick start

If everything is already installed, this is the whole thing:

```bash
brew services start mysql
mysql -u root -e "CREATE DATABASE IF NOT EXISTS contec_pms; CREATE DATABASE IF NOT EXISTS contec_pms_test;"

cd /Users/somya/somu/java_project
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

Then open <http://localhost:8080/swagger-ui.html> and log in as `admin@contec.com` / `Admin@123`.

If any of that fails, follow the detailed steps below.

---

## 3. Step by step setup

### Step 1 — Start MySQL

```bash
brew services start mysql
```

Check that it is really up:

```bash
mysqladmin -u root status
```

If you see server statistics, MySQL is running. If you get `Access denied`, your root user has a
password — that is fine, just add `-p` to the commands below and see [Step 3](#step-3--tell-the-app-your-database-password).

### Step 2 — Create the two databases

The application uses `contec_pms`. The tests use a separate `contec_pms_test` so they never touch
your real data.

```bash
mysql -u root -e "CREATE DATABASE IF NOT EXISTS contec_pms CHARACTER SET utf8mb4;
                  CREATE DATABASE IF NOT EXISTS contec_pms_test CHARACTER SET utf8mb4;"
```

Confirm both exist:

```bash
mysql -u root -e "SHOW DATABASES LIKE 'contec_pms%';"
```

You do **not** need to create any tables. Flyway creates them automatically on first start.

### Step 3 — Tell the app your database password

Skip this if your MySQL root user has no password (the default for a fresh `brew install mysql`).

Otherwise, set these in the same terminal you will run the app from:

```bash
export DB_USERNAME=root
export DB_PASSWORD='your-mysql-password'
export TEST_DB_USERNAME=root
export TEST_DB_PASSWORD='your-mysql-password'
```

To make it permanent, add those lines to `~/.zshrc`.

You can also change the JWT signing key the same way (any string of 32 characters or more):

```bash
export APP_JWT_SECRET='change-me-to-a-long-random-string-please'
```

### Step 4 — Build the project

```bash
cd /Users/somya/somu/java_project
./mvnw clean package -DskipTests
```

The first run takes a few minutes: it downloads Maven 3.9.9 and all libraries.
Later runs take seconds. A successful build ends with `BUILD SUCCESS` and produces
`target/contec-pms-1.0.0.jar`.

> If you see `zsh: permission denied: ./mvnw`, run `chmod +x mvnw` once.

### Step 5 — Run the application

With sample data (recommended the first time):

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

Without sample data (only the admin account exists):

```bash
./mvnw spring-boot:run
```

Or run the built jar directly:

```bash
java -jar target/contec-pms-1.0.0.jar
```

On startup Flyway creates the tables and inserts the admin user. Watch for these lines:

```
Successfully applied 2 migrations to schema `contec_pms`
Tomcat started on port 8080
Started ContecPmsApplication in 4.2 seconds
```

Leave this terminal running. Open a **second** terminal for the commands below.

### Step 6 — Check that it is alive

```bash
curl http://localhost:8080/actuator/health
```

Expected:

```json
{"status":"UP"}
```

Now confirm that protected endpoints really are protected:

```bash
curl -i http://localhost:8080/api/projects
```

Expected: `HTTP/1.1 401` with `"code":"UNAUTHENTICATED"`. That is correct — you have no token yet.

---

## 4. Logging in

### The accounts you can use

| Email | Password | Role | Available |
| --- | --- | --- | --- |
| `admin@contec.com` | `Admin@123` | ADMIN | always |
| `alex.pm@contec.com` | `Password@123` | PROJECT_MANAGER | `demo` profile |
| `jordan.pm@contec.com` | `Password@123` | PROJECT_MANAGER | `demo` profile |
| `sam.eng@contec.com` | `Password@123` | SITE_ENGINEER | `demo` profile |
| `riley.eng@contec.com` | `Password@123` | SITE_ENGINEER | `demo` profile |
| `taylor.eng@contec.com` | `Password@123` | SITE_ENGINEER | `demo` profile |

In the demo data, Alex manages *Riverside Tower* with Sam and Riley; Jordan manages
*Metro Depot Expansion* with Taylor.

### Option A — Swagger UI (easiest)

1. Open <http://localhost:8080/swagger-ui.html>.
2. Find **Authentication → POST /api/auth/login**, click *Try it out*.
3. Paste:
   ```json
   { "email": "admin@contec.com", "password": "Admin@123" }
   ```
4. Click *Execute* and copy the `accessToken` value from the response.
5. Click the green **Authorize** button at the top right, paste the token, click *Authorize*.

Every request you make from Swagger now carries that token.

### Option B — curl

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@contec.com","password":"Admin@123"}'
```

Response:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9....",
  "tokenType": "Bearer",
  "expiresInSeconds": 3600,
  "user": { "id": 1, "email": "admin@contec.com", "fullName": "Contec Administrator",
            "roles": ["ADMIN"], "active": true }
}
```

Save it to a shell variable so the rest of the guide works:

```bash
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@contec.com","password":"Admin@123"}' | jq -r .accessToken)

echo $ADMIN_TOKEN
```

No `jq`? Run the plain curl, copy the token by hand, then `ADMIN_TOKEN='eyJhbGci...'`.

Use it on every request:

```bash
curl -s http://localhost:8080/api/projects -H "Authorization: Bearer $ADMIN_TOKEN"
```

**The token expires after one hour.** When requests start returning 401, log in again.

---

## 5. Using the API: a full walkthrough

This creates a project from scratch and walks a task through its entire life.
Run the app **without** the demo profile for a clean slate, or just follow along on top of the demo data.

### 5.1 Log in as the administrator

```bash
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@contec.com","password":"Admin@123"}' | jq -r .accessToken)
```

### 5.2 Create a project manager and a site engineer

```bash
curl -s -X POST http://localhost:8080/api/users \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{
        "email": "maya.pm@contec.com",
        "password": "Password@123",
        "fullName": "Maya Iyer",
        "roles": ["PROJECT_MANAGER"]
      }' | jq

curl -s -X POST http://localhost:8080/api/users \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{
        "email": "dev.eng@contec.com",
        "password": "Password@123",
        "fullName": "Dev Sharma",
        "roles": ["SITE_ENGINEER"]
      }' | jq
```

Note the `id` returned for each. Store them:

```bash
PM_ID=2      # replace with the real id from the response
ENG_ID=3     # replace with the real id from the response
```

Only an ADMIN can create users. A project manager trying this gets `403`.

### 5.3 Create a project and give it a manager

```bash
PROJECT_ID=$(curl -s -X POST http://localhost:8080/api/projects \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d "{
        \"name\": \"Harbour Bridge Retrofit\",
        \"description\": \"Strengthening works on the south approach.\",
        \"location\": \"South Harbour\",
        \"startDate\": \"2026-10-01\",
        \"expectedCompletionDate\": \"2027-04-30\",
        \"status\": \"ACTIVE\",
        \"managerId\": $PM_ID
      }" | jq -r .id)

echo "project = $PROJECT_ID"
```

`managerId` adds that project manager as the project's `MANAGER`. If a project manager creates a
project themselves, they become its manager automatically and `managerId` is not needed.

### 5.4 Log in as the project manager and add the engineer

```bash
PM_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"maya.pm@contec.com","password":"Password@123"}' | jq -r .accessToken)

curl -s -X POST http://localhost:8080/api/projects/$PROJECT_ID/members \
  -H "Authorization: Bearer $PM_TOKEN" -H 'Content-Type: application/json' \
  -d "{ \"userId\": $ENG_ID, \"projectRole\": \"ENGINEER\" }" | jq
```

A user must be a member of a project before they can see it or be given tasks on it.

Check the member list:

```bash
curl -s http://localhost:8080/api/projects/$PROJECT_ID/members \
  -H "Authorization: Bearer $PM_TOKEN" | jq
```

### 5.5 Create a task and assign it

```bash
TASK_ID=$(curl -s -X POST http://localhost:8080/api/projects/$PROJECT_ID/tasks \
  -H "Authorization: Bearer $PM_TOKEN" -H 'Content-Type: application/json' \
  -d "{
        \"title\": \"Install bearing plates\",
        \"description\": \"Spans 3 to 5.\",
        \"priority\": \"HIGH\",
        \"expectedCompletionDate\": \"2026-12-15\",
        \"assigneeId\": $ENG_ID
      }" | jq -r .id)

echo "task = $TASK_ID"
```

The task starts as `TODO` with `progress: 0`.

You can also create it unassigned and assign later:

```bash
curl -s -X POST http://localhost:8080/api/tasks/$TASK_ID/assign \
  -H "Authorization: Bearer $PM_TOKEN" -H 'Content-Type: application/json' \
  -d "{ \"assigneeId\": $ENG_ID, \"version\": 0 }" | jq
```

The assignee must be a `SITE_ENGINEER` **and** a member of that project, otherwise you get `422`.

### 5.6 Log in as the engineer and do the work

```bash
ENG_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"dev.eng@contec.com","password":"Password@123"}' | jq -r .accessToken)
```

Start the task (`TODO → IN_PROGRESS`):

```bash
curl -s -X POST http://localhost:8080/api/tasks/$TASK_ID/start \
  -H "Authorization: Bearer $ENG_TOKEN" | jq '{status, progress, version}'
```

Report progress. You must send the current `version` — read it from the response above:

```bash
VERSION=$(curl -s http://localhost:8080/api/tasks/$TASK_ID \
  -H "Authorization: Bearer $ENG_TOKEN" | jq -r .version)

curl -s -X PATCH http://localhost:8080/api/tasks/$TASK_ID/progress \
  -H "Authorization: Bearer $ENG_TOKEN" -H 'Content-Type: application/json' \
  -d "{ \"progress\": 60, \"note\": \"Plates 1-8 seated.\", \"version\": $VERSION }" \
  | jq '{status, progress, version}'
```

Mark it complete (`IN_PROGRESS → COMPLETED`, progress jumps to 100):

```bash
VERSION=$(curl -s http://localhost:8080/api/tasks/$TASK_ID \
  -H "Authorization: Bearer $ENG_TOKEN" | jq -r .version)

curl -s -X POST http://localhost:8080/api/tasks/$TASK_ID/complete \
  -H "Authorization: Bearer $ENG_TOKEN" -H 'Content-Type: application/json' \
  -d "{ \"note\": \"All plates installed and torqued.\", \"version\": $VERSION }" \
  | jq '{status, progress, completedAt}'
```

### 5.7 The manager rejects it

A rejection **must** have a reason — without one you get `400`.

```bash
VERSION=$(curl -s http://localhost:8080/api/tasks/$TASK_ID \
  -H "Authorization: Bearer $PM_TOKEN" | jq -r .version)

curl -s -X POST http://localhost:8080/api/tasks/$TASK_ID/reject \
  -H "Authorization: Bearer $PM_TOKEN" -H 'Content-Type: application/json' \
  -d "{ \"reason\": \"Torque records missing for span 5.\", \"version\": $VERSION }" \
  | jq '{status, rejectionReason, rejectedBy}'
```

### 5.8 The engineer reworks it, the manager approves

```bash
# back to work: REJECTED -> IN_PROGRESS
curl -s -X POST http://localhost:8080/api/tasks/$TASK_ID/start \
  -H "Authorization: Bearer $ENG_TOKEN" | jq '{status}'

# complete again
VERSION=$(curl -s http://localhost:8080/api/tasks/$TASK_ID \
  -H "Authorization: Bearer $ENG_TOKEN" | jq -r .version)
curl -s -X POST http://localhost:8080/api/tasks/$TASK_ID/complete \
  -H "Authorization: Bearer $ENG_TOKEN" -H 'Content-Type: application/json' \
  -d "{ \"note\": \"Torque records uploaded.\", \"version\": $VERSION }" | jq '{status}'

# approve
VERSION=$(curl -s http://localhost:8080/api/tasks/$TASK_ID \
  -H "Authorization: Bearer $PM_TOKEN" | jq -r .version)
curl -s -X POST http://localhost:8080/api/tasks/$TASK_ID/approve \
  -H "Authorization: Bearer $PM_TOKEN" -H 'Content-Type: application/json' \
  -d "{ \"note\": \"Inspected on site.\", \"version\": $VERSION }" \
  | jq '{status, approvedBy, approvedAt}'
```

`APPROVED` is the end of the line. Any further transition returns `409`.

### 5.9 Read the activity history

```bash
curl -s "http://localhost:8080/api/tasks/$TASK_ID/activities" \
  -H "Authorization: Bearer $PM_TOKEN" | jq '.content[] | {activityType, actor: .actor.email, createdAt}'
```

You get the full trail, newest first: created, assigned, started, progress updated, completed,
rejected, started, completed, approved — each with who did it and when.

### 5.10 List and filter tasks

```bash
# everything in the project
curl -s "http://localhost:8080/api/projects/$PROJECT_ID/tasks" \
  -H "Authorization: Bearer $PM_TOKEN" | jq '{totalElements, content: [.content[].title]}'

# only in-progress work
curl -s "http://localhost:8080/api/projects/$PROJECT_ID/tasks?status=IN_PROGRESS" \
  -H "Authorization: Bearer $PM_TOKEN" | jq .totalElements

# one engineer's high priority tasks, highest priority first, 10 per page
curl -s "http://localhost:8080/api/projects/$PROJECT_ID/tasks?assigneeId=$ENG_ID&priority=HIGH&sort=priority,desc&page=0&size=10" \
  -H "Authorization: Bearer $PM_TOKEN" | jq
```

Supported query parameters: `status`, `assigneeId`, `priority`, `search`, `page`, `size`, `sort`.

### 5.11 See authorization working

```bash
# an engineer cannot approve  ->  403
curl -s -X POST http://localhost:8080/api/tasks/$TASK_ID/approve \
  -H "Authorization: Bearer $ENG_TOKEN" -H 'Content-Type: application/json' \
  -d '{ "version": 0 }' | jq

# a user only sees their own projects
curl -s http://localhost:8080/api/projects -H "Authorization: Bearer $ENG_TOKEN" \
  | jq '{totalElements, names: [.content[].name]}'

# asking for someone else's project  ->  403
curl -s http://localhost:8080/api/projects/999 -H "Authorization: Bearer $ENG_TOKEN" | jq
```

---

## 6. Who is allowed to do what

| Action | ADMIN | PROJECT_MANAGER | SITE_ENGINEER |
| --- | :---: | :---: | :---: |
| Create users | yes | no | no |
| List users | yes | yes | no |
| Create a project | yes | yes | no |
| Update a project | yes | only projects they manage | no |
| See a project | yes | only their projects | only their projects |
| Add or remove members | yes | only projects they manage | no |
| Create or edit a task | yes | only projects they manage | no |
| Assign a task | yes | only projects they manage | no |
| Start / report progress / complete | yes | no | only their own tasks |
| Approve or reject | yes | only projects they manage | no |
| Read activity history | yes | only their projects | only their projects |

The important rule: **being a PROJECT_MANAGER is not enough.** You must also be a member of that
specific project with the `MANAGER` role. A manager of project A has no access at all to project B.

---

## 7. Understanding `version`

Every task carries a `version` number. It stops two people from overwriting each other.

```
Maya loads task 7   (version 3)
Dev  loads task 7   (version 3)
Dev  saves          -> task 7 is now version 4
Maya saves with version 3  -> 409, her change is refused
```

So: **read the task, use the `version` it returns, send it back with your change.**

If you get this response, someone else changed the task since you loaded it:

```json
{
  "status": 409,
  "code": "STALE_RESOURCE",
  "message": "Task has been modified by someone else (submitted version 3, current version 4). Reload it and try again."
}
```

The fix is always the same — `GET /api/tasks/{id}` again, look at what changed, and resend with the
new version.

`version` is required on: update task, assign, progress, complete, approve, reject.
It is not needed on `start` (there is no body) or on any `GET`.

---

## 8. Running the tests

MySQL must be running and `contec_pms_test` must exist (see [Step 2](#step-2--create-the-two-databases)).
The tests never touch `contec_pms`.

```bash
./mvnw test
```

Run one test class:

```bash
./mvnw test -Dtest=TaskWorkflowIT
```

Run one test method:

```bash
./mvnw test -Dtest=TaskApprovalIT#siteEngineerCannotApprove
```

What the suite covers:

| Test class | What it proves |
| --- | --- |
| `TaskStatusTransitionTest` | every legal and illegal status move |
| `AuthenticationIT` | good and bad logins, protected routes |
| `ProjectAccessIT` | you cannot reach a project you are not on |
| `TaskWorkflowIT` | the whole lifecycle, and that shortcuts are refused |
| `TaskProgressIT` | progress 0–100, wrong engineer, completed task |
| `TaskApprovalIT` | approval, rejection with a reason, engineer refused |
| `TaskAssignmentIT` | assignment needs the right role and membership |
| `ActivityHistoryIT` | every action leaves an activity record |
| `TransactionRollbackIT` | a failure leaves no half-finished update |
| `OptimisticLockingIT` | stale updates are refused, newer change survives |
| `TaskListingIT` | paging, sorting, filtering |

Full build plus tests:

```bash
./mvnw clean verify
```

---

## 9. Stopping and resetting

Stop the application: press `Ctrl+C` in the terminal running it.

Stop MySQL:

```bash
brew services stop mysql
```

Wipe all data and start over (this deletes everything in both schemas):

```bash
mysql -u root -e "DROP DATABASE contec_pms; CREATE DATABASE contec_pms CHARACTER SET utf8mb4;
                  DROP DATABASE contec_pms_test; CREATE DATABASE contec_pms_test CHARACTER SET utf8mb4;"
```

Next start, Flyway rebuilds the schema from scratch.

Look at the data directly:

```bash
mysql -u root contec_pms -e "SELECT id, title, status, progress FROM tasks;"
mysql -u root contec_pms -e "SELECT activity_type, actor_id, created_at FROM task_activities ORDER BY id;"
```

---

## 10. Troubleshooting

### The app will not start

| Message | What it means | Fix |
| --- | --- | --- |
| `Communications link failure` | MySQL is not running | `brew services start mysql` |
| `Unknown database 'contec_pms'` | schema missing | run the `CREATE DATABASE` from Step 2 |
| `Access denied for user 'root'@'localhost'` | wrong password | `export DB_PASSWORD='...'` (Step 3) |
| `Web server failed to start. Port 8080 was already in use` | something else is on 8080 | `./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8081`, or `lsof -ti:8080 \| xargs kill` |
| `Validate failed: Migration checksum mismatch` | a migration file was edited after it ran | drop and recreate the database (Section 9) |
| `Schema-validation: missing table [tasks]` | Flyway did not run, or you are pointed at the wrong schema | check the URL in `DB_URL`, then recreate the database |
| `app.jwt.secret must be at least 32 bytes` | `APP_JWT_SECRET` is too short | use 32 characters or more |
| `permission denied: ./mvnw` | script is not executable | `chmod +x mvnw` |
| `Unsupported class file major version` | wrong Java | `java -version` must be 17+ |

### API responses you did not expect

| Status | Code | Why | What to do |
| --- | --- | --- | --- |
| 401 | `UNAUTHENTICATED` | no token, expired token, or wrong password | log in again; tokens last one hour |
| 403 | `ACCESS_DENIED` | your role or membership does not allow it | check Section 6; you may need to be added to the project |
| 404 | `RESOURCE_NOT_FOUND` | wrong id | check the id |
| 409 | `INVALID_STATUS_TRANSITION` | illegal workflow step, e.g. completing a `TODO` task | the message lists the allowed moves |
| 409 | `STALE_RESOURCE` | your `version` is out of date | reload the task and resend — Section 7 |
| 422 | `ASSIGNEE_NOT_A_MEMBER` | that user is not on the project | add them as a member first |
| 422 | `INVALID_ASSIGNEE_ROLE` | you assigned a non-engineer | tasks go to `SITE_ENGINEER` users only |
| 422 | `INVALID_PROJECT_DATES` | completion date is before start date | fix the dates |
| 400 | `VALIDATION_FAILED` | a field is missing or out of range | read `fieldErrors` in the response |
| 400 | `MALFORMED_REQUEST` | bad JSON or an unknown enum value | check spelling, e.g. `IN_PROGRESS` not `INPROGRESS` |

Every error looks like this, so you can always tell what went wrong:

```json
{
  "timestamp": "2026-09-14T10:15:30Z",
  "status": 409,
  "error": "Conflict",
  "code": "INVALID_STATUS_TRANSITION",
  "message": "Cannot move a task from TODO to COMPLETED. Allowed from TODO: [IN_PROGRESS]",
  "path": "/api/tasks/7/complete"
}
```

### Tests fail

| Message | Fix |
| --- | --- |
| `Unknown database 'contec_pms_test'` | create it (Step 2) |
| `Access denied for user` | `export TEST_DB_PASSWORD='...'` |
| `Communications link failure` | start MySQL |

---

## 11. Endpoint cheat sheet

Base URL: `http://localhost:8080`. Every endpoint except login needs
`Authorization: Bearer <token>`.

**Authentication**

```
POST   /api/auth/login              { email, password }
GET    /api/auth/me
```

**Users** (ADMIN, and PROJECT_MANAGER for reads)

```
POST   /api/users                   { email, password, fullName, roles[] }
GET    /api/users?role=SITE_ENGINEER&page=0&size=20
GET    /api/users/{userId}
```

**Projects**

```
POST   /api/projects                { name, description, location, startDate,
                                      expectedCompletionDate, status, managerId? }
GET    /api/projects?status=ACTIVE&search=bridge&page=0&size=20&sort=createdAt,desc
GET    /api/projects/{projectId}
PUT    /api/projects/{projectId}    { name, description, location, startDate,
                                      expectedCompletionDate, status, version? }
```

**Members**

```
POST   /api/projects/{projectId}/members            { userId, projectRole }
GET    /api/projects/{projectId}/members
DELETE /api/projects/{projectId}/members/{userId}
```

**Tasks**

```
POST   /api/projects/{projectId}/tasks   { title, description, priority,
                                           expectedCompletionDate, assigneeId? }
GET    /api/projects/{projectId}/tasks?status=&assigneeId=&priority=&search=&page=&size=&sort=
GET    /api/tasks/{taskId}
PUT    /api/tasks/{taskId}               { title, description, priority,
                                           expectedCompletionDate, version }
POST   /api/tasks/{taskId}/assign        { assigneeId, version }
POST   /api/tasks/{taskId}/start         (no body)
PATCH  /api/tasks/{taskId}/progress      { progress, note?, version }
POST   /api/tasks/{taskId}/complete      { note?, version }
POST   /api/tasks/{taskId}/approve       { note?, version }
POST   /api/tasks/{taskId}/reject        { reason, version }
GET    /api/tasks/{taskId}/activities?page=0&size=50
```

**Task workflow**

```
TODO ──▶ IN_PROGRESS ──▶ COMPLETED ──▶ APPROVED   (end)
            ▲                    └────▶ REJECTED
            └────────────────────────────┘  (rework)
```

Values you can use:

* `status` (project): `PLANNED`, `ACTIVE`, `ON_HOLD`, `COMPLETED`, `CANCELLED`
* `status` (task): `TODO`, `IN_PROGRESS`, `COMPLETED`, `APPROVED`, `REJECTED`
* `priority`: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`
* `roles`: `ADMIN`, `PROJECT_MANAGER`, `SITE_ENGINEER`
* `projectRole`: `MANAGER`, `ENGINEER`

---

There is also a Postman collection at
[`docs/contec-pms.postman_collection.json`](docs/contec-pms.postman_collection.json).
Import it, run **Login**, and the token, project id and task id fill themselves in as you go.
