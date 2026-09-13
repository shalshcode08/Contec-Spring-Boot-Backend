# Getting Started

A complete, step-by-step guide to running this project and using its API.
Works on **Windows, macOS and Linux** — every step shows the command for each.

---

## Contents

1. [What you need](#1-what-you-need)
2. [Quick start](#2-quick-start)
3. [Step by step setup](#3-step-by-step-setup)
4. [Logging in](#4-logging-in)
5. [Using the API: a full walkthrough](#5-using-the-api-a-full-walkthrough)
6. [Windows / PowerShell commands](#6-windows--powershell-commands)
7. [Who is allowed to do what](#7-who-is-allowed-to-do-what)
8. [Understanding `version`](#8-understanding-version)
9. [Running the tests](#9-running-the-tests)
10. [Stopping and resetting](#10-stopping-and-resetting)
11. [Troubleshooting](#11-troubleshooting)
12. [Endpoint cheat sheet](#12-endpoint-cheat-sheet)

---

## 1. What you need

| Tool | Version | Check it with |
| --- | --- | --- |
| Java (JDK) | 17 or newer | `java -version` |
| MySQL | 8.0 or newer | `mysql --version` |
| Maven | **not needed** | the project ships a wrapper |

If something is missing:

| | Java 17 | MySQL 8 |
| --- | --- | --- |
| **Windows** | [Adoptium Temurin 17](https://adoptium.net/) installer | [MySQL Installer for Windows](https://dev.mysql.com/downloads/installer/) |
| **macOS** | `brew install openjdk@17` | `brew install mysql` |
| **Linux** | `sudo apt install openjdk-17-jdk` | `sudo apt install mysql-server` |

You also need internet access the **first** time you build: the wrapper downloads Maven and the
project's libraries into your user folder (`~/.m2` or `C:\Users\<you>\.m2`). Nothing is installed
system-wide.

> **Which terminal?**
> Windows — PowerShell or Command Prompt works. If you have **Git Bash**, every macOS/Linux command
> in this guide works there unchanged, which is the easiest path.
> macOS/Linux — any terminal.

Optional: [`jq`](https://jqlang.github.io/jq/) makes JSON readable in the terminal. The guide works without it.

---

## 2. Quick start

**Windows (PowerShell)**

```powershell
net start MySQL80
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS contec_pms; CREATE DATABASE IF NOT EXISTS contec_pms_test;"
cd C:\path\to\java_project
.\mvnw.cmd spring-boot:run
```

**macOS**

```bash
brew services start mysql
mysql -u root -e "CREATE DATABASE IF NOT EXISTS contec_pms; CREATE DATABASE IF NOT EXISTS contec_pms_test;"
cd /Users/somya/somu/java_project
./mvnw spring-boot:run
```

**Linux**

```bash
sudo systemctl start mysql
sudo mysql -e "CREATE DATABASE IF NOT EXISTS contec_pms; CREATE DATABASE IF NOT EXISTS contec_pms_test;"
cd ~/java_project
./mvnw spring-boot:run
```

Then open <http://localhost:8080/swagger-ui.html> and log in as `admin@contec.com` / `Admin@123`.

If anything fails, follow the detailed steps below.

---

## 3. Step by step setup

### Step 1 — Start MySQL

| | Command |
| --- | --- |
| **Windows** | `net start MySQL80` in an **administrator** terminal (the service may be named `MySQL84` or similar — check *Services* in the Start menu). It usually starts automatically after installation. |
| **macOS** | `brew services start mysql` |
| **Linux** | `sudo systemctl start mysql` |

Check it is really up:

```bash
mysqladmin -u root -p status
```

Statistics mean MySQL is running.

### Step 2 — Create the two databases

The application uses `contec_pms`. The tests use a separate `contec_pms_test` so they never touch
your real data.

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS contec_pms CHARACTER SET utf8mb4; CREATE DATABASE IF NOT EXISTS contec_pms_test CHARACTER SET utf8mb4;"
```

On Linux you may need `sudo mysql` instead of `mysql -u root -p`.
Drop the `-p` if your root user has no password (common on a fresh macOS install).

Confirm:

```bash
mysql -u root -p -e "SHOW DATABASES LIKE 'contec_pms%';"
```

You do **not** need to create tables. Flyway builds them on first start.

### Step 3 — Tell the app your database password

Skip this if your MySQL root user has no password.

Otherwise set these in the **same terminal** you will run the app from:

**Windows (PowerShell)**

```powershell
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "your-mysql-password"
$env:TEST_DB_USERNAME = "root"
$env:TEST_DB_PASSWORD = "your-mysql-password"
```

**Windows (Command Prompt)**

```cmd
set DB_USERNAME=root
set DB_PASSWORD=your-mysql-password
set TEST_DB_USERNAME=root
set TEST_DB_PASSWORD=your-mysql-password
```

**macOS / Linux**

```bash
export DB_USERNAME=root
export DB_PASSWORD='your-mysql-password'
export TEST_DB_USERNAME=root
export TEST_DB_PASSWORD='your-mysql-password'
```

These last only for that terminal session. To make them permanent, use *System Properties →
Environment Variables* on Windows, or add the `export` lines to `~/.zshrc` / `~/.bashrc`.

You can set the JWT signing key the same way (any string of 32 characters or more):
`APP_JWT_SECRET`.

### Step 4 — Build the project

| | Command |
| --- | --- |
| **Windows** | `.\mvnw.cmd clean package -DskipTests` |
| **macOS / Linux** | `./mvnw clean package -DskipTests` |

The first run takes a few minutes while Maven and the libraries download. Later runs take seconds.
Success ends with `BUILD SUCCESS` and produces `target/contec-pms-1.0.0.jar`.

> macOS/Linux: if you see `permission denied: ./mvnw`, run `chmod +x mvnw` once.

### Step 5 — Run the application

| | Command |
| --- | --- |
| **Windows** | `.\mvnw.cmd spring-boot:run` |
| **macOS / Linux** | `./mvnw spring-boot:run` |

Or run the jar directly (same everywhere):

```bash
java -jar target/contec-pms-1.0.0.jar
```

On startup Flyway creates the tables and inserts sample users, projects and tasks. Look for:

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

Expected: `{"status":"UP"}`

Now confirm that protected endpoints really are protected:

```bash
curl -i http://localhost:8080/api/projects
```

Expected: `HTTP/1.1 401` with `"code":"UNAUTHENTICATED"`. Correct — you have no token yet.

> **PowerShell note:** `curl` there is an alias for a different command. Use `curl.exe`, or use the
> `Invoke-RestMethod` versions in [section 6](#6-windows--powershell-commands), or just use Swagger UI.

---

## 4. Logging in

### Accounts created by the migration

| Email | Password | Role |
| --- | --- | --- |
| `admin@contec.com` | `Admin@123` | ADMIN |
| `alex.pm@contec.com` | `Password@123` | PROJECT_MANAGER |
| `jordan.pm@contec.com` | `Password@123` | PROJECT_MANAGER |
| `sam.eng@contec.com` | `Password@123` | SITE_ENGINEER |
| `riley.eng@contec.com` | `Password@123` | SITE_ENGINEER |
| `taylor.eng@contec.com` | `Password@123` | SITE_ENGINEER |

Alex manages *Riverside Tower* with Sam and Riley. Jordan manages *Metro Depot Expansion* with Taylor.

### Option A — Swagger UI (easiest, same on every OS)

1. Open <http://localhost:8080/swagger-ui.html>
2. **Authentication → POST /api/auth/login** → *Try it out*
3. Paste `{ "email": "admin@contec.com", "password": "Admin@123" }` → *Execute*
4. Copy the `accessToken` from the response
5. Click **Authorize** (top right), paste the token, *Authorize*

Every request from Swagger now carries that token.

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
            "role": "ADMIN", "active": true }
}
```

Save it so the rest of the guide works:

```bash
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@contec.com","password":"Admin@123"}' | jq -r .accessToken)
```

No `jq`? Run the plain curl, copy the token by hand, then `ADMIN_TOKEN='eyJhbGci...'`.

Use it on every request:

```bash
curl -s http://localhost:8080/api/projects -H "Authorization: Bearer $ADMIN_TOKEN"
```

**Tokens expire after one hour.** When requests start returning 401, log in again.

---

## 5. Using the API: a full walkthrough

This creates a project from scratch and walks a task through its whole life.
Commands use bash (macOS, Linux, Git Bash) — PowerShell equivalents are in [section 6](#6-windows--powershell-commands).

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
  -d '{"email":"maya.pm@contec.com","password":"Password@123","fullName":"Maya Iyer","role":"PROJECT_MANAGER"}'

curl -s -X POST http://localhost:8080/api/users \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"email":"dev.eng@contec.com","password":"Password@123","fullName":"Dev Sharma","role":"SITE_ENGINEER"}'
```

Each response contains an `id`. Save them (use the real numbers you got back):

```bash
PM_ID=7
ENG_ID=8
```

Only an ADMIN can create users; a project manager gets `403`.

### 5.3 Create a project and give it a manager

```bash
PROJECT_ID=$(curl -s -X POST http://localhost:8080/api/projects \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"name\":\"Harbour Bridge Retrofit\",\"description\":\"South approach strengthening.\",\"location\":\"South Harbour\",\"startDate\":\"2026-10-01\",\"expectedCompletionDate\":\"2027-04-30\",\"status\":\"ACTIVE\",\"managerId\":$PM_ID}" \
  | jq -r .id)

echo "project = $PROJECT_ID"
```

`managerId` adds that project manager to the project. A project manager who creates a project
joins it automatically and does not need `managerId`.

### 5.4 Log in as the manager and add the engineer

```bash
PM_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"maya.pm@contec.com","password":"Password@123"}' | jq -r .accessToken)

curl -s -X POST http://localhost:8080/api/projects/$PROJECT_ID/members \
  -H "Authorization: Bearer $PM_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"userId\":$ENG_ID}"
```

A user must be a member of a project before they can see it or receive tasks on it.

See the members:

```bash
curl -s http://localhost:8080/api/projects/$PROJECT_ID/members -H "Authorization: Bearer $PM_TOKEN"
```

### 5.5 Create a task and assign it

```bash
TASK_ID=$(curl -s -X POST http://localhost:8080/api/projects/$PROJECT_ID/tasks \
  -H "Authorization: Bearer $PM_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"title\":\"Install bearing plates\",\"description\":\"Spans 3 to 5.\",\"priority\":\"HIGH\",\"expectedCompletionDate\":\"2026-12-15\",\"assigneeId\":$ENG_ID}" \
  | jq -r .id)

echo "task = $TASK_ID"
```

The task starts as `TODO` with `progress: 0`.

You can also create it unassigned and assign later (this one needs the task's `version`):

```bash
curl -s -X POST http://localhost:8080/api/tasks/$TASK_ID/assign \
  -H "Authorization: Bearer $PM_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"assigneeId\":$ENG_ID,\"version\":0}"
```

The assignee must be a `SITE_ENGINEER` **and** a member of the project, otherwise you get `422`.

### 5.6 Log in as the engineer and do the work

```bash
ENG_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"dev.eng@contec.com","password":"Password@123"}' | jq -r .accessToken)
```

Start the task (`TODO → IN_PROGRESS`):

```bash
curl -s -X POST http://localhost:8080/api/tasks/$TASK_ID/start -H "Authorization: Bearer $ENG_TOKEN"
```

Report progress — this one carries the current `version`:

```bash
VERSION=$(curl -s http://localhost:8080/api/tasks/$TASK_ID -H "Authorization: Bearer $ENG_TOKEN" | jq -r .version)

curl -s -X PATCH http://localhost:8080/api/tasks/$TASK_ID/progress \
  -H "Authorization: Bearer $ENG_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"progress\":60,\"note\":\"Plates 1-8 seated.\",\"version\":$VERSION}"
```

Mark it complete (`IN_PROGRESS → COMPLETED`, progress jumps to 100). No body needed:

```bash
curl -s -X POST http://localhost:8080/api/tasks/$TASK_ID/complete -H "Authorization: Bearer $ENG_TOKEN"
```

### 5.7 The manager rejects it

A rejection **must** have a reason — without one you get `400`.

```bash
curl -s -X POST http://localhost:8080/api/tasks/$TASK_ID/reject \
  -H "Authorization: Bearer $PM_TOKEN" -H 'Content-Type: application/json' \
  -d '{"reason":"Torque records missing for span 5."}'
```

### 5.8 The engineer reworks it, the manager approves

```bash
curl -s -X POST http://localhost:8080/api/tasks/$TASK_ID/start    -H "Authorization: Bearer $ENG_TOKEN"
curl -s -X POST http://localhost:8080/api/tasks/$TASK_ID/complete -H "Authorization: Bearer $ENG_TOKEN"
curl -s -X POST http://localhost:8080/api/tasks/$TASK_ID/approve  -H "Authorization: Bearer $PM_TOKEN"
```

`APPROVED` is the end of the line. Any further transition returns `409`.

### 5.9 Read the activity history

```bash
curl -s "http://localhost:8080/api/tasks/$TASK_ID/activities" -H "Authorization: Bearer $PM_TOKEN"
```

The full trail, newest first: created, assigned, started, progress updated, completed, rejected,
started, completed, approved — each with who did it and when.

### 5.10 List and filter tasks

```bash
# everything in the project
curl -s "http://localhost:8080/api/projects/$PROJECT_ID/tasks" -H "Authorization: Bearer $PM_TOKEN"

# only in-progress work
curl -s "http://localhost:8080/api/projects/$PROJECT_ID/tasks?status=IN_PROGRESS" -H "Authorization: Bearer $PM_TOKEN"

# one engineer's high-priority tasks, 10 per page, newest first
curl -s "http://localhost:8080/api/projects/$PROJECT_ID/tasks?assigneeId=$ENG_ID&priority=HIGH&page=0&size=10&sort=createdAt,desc" \
  -H "Authorization: Bearer $PM_TOKEN"
```

Query parameters: `status`, `priority`, `assigneeId`, `page`, `size`, `sort`.

### 5.11 See authorization working

```bash
# an engineer cannot approve  ->  403
curl -s -X POST http://localhost:8080/api/tasks/$TASK_ID/approve -H "Authorization: Bearer $ENG_TOKEN"

# a user only sees their own projects
curl -s http://localhost:8080/api/projects -H "Authorization: Bearer $ENG_TOKEN"

# asking for someone else's project  ->  403
curl -s http://localhost:8080/api/projects/1 -H "Authorization: Bearer $ENG_TOKEN"
```

---

## 6. Windows / PowerShell commands

In PowerShell, `curl` is an alias for `Invoke-WebRequest`, and single quotes do not work the same
way. You have three options:

1. **Swagger UI** — no commands at all, the easiest path.
2. **Git Bash** — every command in section 5 works exactly as written.
3. **PowerShell with `Invoke-RestMethod`** — shown below.

```powershell
$base = "http://localhost:8080"

# log in
$login  = Invoke-RestMethod -Uri "$base/api/auth/login" -Method Post -ContentType 'application/json' `
          -Body '{"email":"admin@contec.com","password":"Admin@123"}'
$admin  = @{ Authorization = "Bearer $($login.accessToken)" }

# list projects
Invoke-RestMethod -Uri "$base/api/projects" -Headers $admin

# create a user
$body = @{ email = "maya.pm@contec.com"; password = "Password@123"
           fullName = "Maya Iyer"; role = "PROJECT_MANAGER" } | ConvertTo-Json
$pm = Invoke-RestMethod -Uri "$base/api/users" -Method Post -Headers $admin -ContentType 'application/json' -Body $body

# create a project
$body = @{ name = "Harbour Bridge Retrofit"; description = "South approach."
           location = "South Harbour"; startDate = "2026-10-01"
           expectedCompletionDate = "2027-04-30"; status = "ACTIVE"; managerId = $pm.id } | ConvertTo-Json
$project = Invoke-RestMethod -Uri "$base/api/projects" -Method Post -Headers $admin -ContentType 'application/json' -Body $body

# log in as the manager
$login   = Invoke-RestMethod -Uri "$base/api/auth/login" -Method Post -ContentType 'application/json' `
           -Body '{"email":"maya.pm@contec.com","password":"Password@123"}'
$manager = @{ Authorization = "Bearer $($login.accessToken)" }

# add a member, create a task
$body = @{ userId = 8 } | ConvertTo-Json
Invoke-RestMethod -Uri "$base/api/projects/$($project.id)/members" -Method Post -Headers $manager -ContentType 'application/json' -Body $body

$body = @{ title = "Install bearing plates"; priority = "HIGH"; assigneeId = 8 } | ConvertTo-Json
$task = Invoke-RestMethod -Uri "$base/api/projects/$($project.id)/tasks" -Method Post -Headers $manager -ContentType 'application/json' -Body $body

# work on it as the engineer
$login    = Invoke-RestMethod -Uri "$base/api/auth/login" -Method Post -ContentType 'application/json' `
            -Body '{"email":"dev.eng@contec.com","password":"Password@123"}'
$engineer = @{ Authorization = "Bearer $($login.accessToken)" }

$task = Invoke-RestMethod -Uri "$base/api/tasks/$($task.id)/start" -Method Post -Headers $engineer

$body = @{ progress = 60; note = "Plates seated."; version = $task.version } | ConvertTo-Json
$task = Invoke-RestMethod -Uri "$base/api/tasks/$($task.id)/progress" -Method Patch -Headers $engineer -ContentType 'application/json' -Body $body

Invoke-RestMethod -Uri "$base/api/tasks/$($task.id)/complete" -Method Post -Headers $engineer
Invoke-RestMethod -Uri "$base/api/tasks/$($task.id)/approve"  -Method Post -Headers $manager
Invoke-RestMethod -Uri "$base/api/tasks/$($task.id)/activities" -Headers $manager | Select-Object -ExpandProperty content
```

The pattern: build the body with `@{ ... } | ConvertTo-Json`, pass the token in a `$headers`
hashtable, and use `Invoke-RestMethod` so the JSON comes back as an object you can read fields from.

---

## 7. Who is allowed to do what

| Action | ADMIN | PROJECT_MANAGER | SITE_ENGINEER |
| --- | :---: | :---: | :---: |
| Create users | yes | no | no |
| List users | yes | yes | no |
| Create a project | yes | yes | no |
| Update a project | yes | only their projects | no |
| See a project | yes | only their projects | only their projects |
| Add members | yes | only their projects | no |
| Create or edit a task | yes | only their projects | no |
| Assign a task | yes | only their projects | no |
| Start / progress / complete | yes | no | only tasks assigned to them |
| Approve or reject | yes | only their projects | no |
| Read activity history | yes | only their projects | only their projects |

"Their projects" means projects the user has been added to as a member.
The important rule: **being a PROJECT_MANAGER is not enough.** A manager who is not a member of a
project has no access to it at all.

---

## 8. Understanding `version`

Every task carries a `version` number that stops two people overwriting each other.

```
Maya loads task 7   (version 3)
Dev  loads task 7   (version 3)
Dev  saves          -> task 7 is now version 4
Maya saves with version 3  -> 409, her change is refused
```

So: **read the task, use the `version` it returns, send it back with your change.**

```json
{
  "status": 409,
  "code": "STALE_RESOURCE",
  "message": "Task has been modified by someone else (submitted version 3, current version 4). Reload it and try again."
}
```

The fix is always the same — `GET /api/tasks/{id}` again, check what changed, resend with the new version.

`version` is required on the three endpoints that change task content: **update**, **assign** and
**progress**. Start, complete, approve and reject do not need it — the workflow rules already stop
the same step happening twice.

---

## 9. Running the tests

MySQL must be running and `contec_pms_test` must exist ([Step 2](#step-2--create-the-two-databases)).
The tests never touch `contec_pms`.

| | Command |
| --- | --- |
| **Windows** | `.\mvnw.cmd test` |
| **macOS / Linux** | `./mvnw test` |

One class, or one method:

```bash
./mvnw test -Dtest=TaskWorkflowIT
./mvnw test -Dtest=TaskApprovalIT#siteEngineerCannotApprove
```

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
| `OptimisticLockingIT` | stale updates refused, newer change survives |
| `TaskListingIT` | paging, sorting, filtering |

---

## 10. Stopping and resetting

Stop the application: `Ctrl+C` in the terminal running it.

Stop MySQL:

| | Command |
| --- | --- |
| **Windows** | `net stop MySQL80` (administrator terminal) |
| **macOS** | `brew services stop mysql` |
| **Linux** | `sudo systemctl stop mysql` |

Wipe all data and start over — this deletes everything in both schemas:

```bash
mysql -u root -p -e "DROP DATABASE contec_pms; CREATE DATABASE contec_pms CHARACTER SET utf8mb4; DROP DATABASE contec_pms_test; CREATE DATABASE contec_pms_test CHARACTER SET utf8mb4;"
```

Next start, Flyway rebuilds the schema and sample data from scratch.

Look at the data directly:

```bash
mysql -u root -p contec_pms -e "SELECT id, title, status, progress FROM tasks;"
mysql -u root -p contec_pms -e "SELECT activity_type, actor_id, created_at FROM task_activities ORDER BY id;"
```

---

## 11. Troubleshooting

### The app will not start

| Message | What it means | Fix |
| --- | --- | --- |
| `Communications link failure` | MySQL is not running | start it (Step 1) |
| `Unknown database 'contec_pms'` | schema missing | run the `CREATE DATABASE` from Step 2 |
| `Access denied for user 'root'@'localhost'` | wrong password | set `DB_PASSWORD` (Step 3) |
| `Port 8080 was already in use` | something else is on 8080 | run with `--server.port=8081`, or free the port: Windows `netstat -ano \| findstr :8080` then `taskkill /PID <pid> /F`; macOS/Linux `lsof -ti:8080 \| xargs kill` |
| `Validate failed: Migration checksum mismatch` | a migration file changed after it ran | drop and recreate the database (Section 10) |
| `Schema-validation: missing table [tasks]` | Flyway did not run, or the wrong schema | check `DB_URL`, then recreate the database |
| `app.jwt.secret must be at least 32 characters` | `APP_JWT_SECRET` too short | use 32 characters or more |
| `permission denied: ./mvnw` | script not executable (macOS/Linux) | `chmod +x mvnw` |
| `'mvnw' is not recognized` | wrong command on Windows | use `.\mvnw.cmd` |
| `Unsupported class file major version` | wrong Java | `java -version` must be 17+ |

### API responses you did not expect

| Status | Code | Why | What to do |
| --- | --- | --- | --- |
| 401 | `UNAUTHENTICATED` | no token, expired token, or wrong password | log in again; tokens last one hour |
| 403 | `ACCESS_DENIED` | your role or membership does not allow it | see Section 7 — you may need to be added to the project |
| 404 | `RESOURCE_NOT_FOUND` | wrong id | check the id |
| 409 | `INVALID_STATUS_TRANSITION` | illegal workflow step, e.g. completing a `TODO` task | the message lists the allowed moves |
| 409 | `STALE_RESOURCE` | your `version` is out of date | reload the task and resend — Section 8 |
| 422 | `ASSIGNEE_NOT_A_MEMBER` | that user is not on the project | add them as a member first |
| 422 | `INVALID_ASSIGNEE_ROLE` | you assigned a non-engineer | tasks go to `SITE_ENGINEER` users only |
| 422 | `INVALID_PROJECT_DATES` | completion date before start date | fix the dates |
| 400 | `VALIDATION_FAILED` | a field is missing or out of range | read `fieldErrors` in the response |
| 400 | `MALFORMED_REQUEST` | bad JSON or an unknown enum value | check spelling, e.g. `IN_PROGRESS` not `INPROGRESS` |

Every error has the same shape, so you can always tell what went wrong:

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
| `Access denied for user` | set `TEST_DB_PASSWORD` |
| `Communications link failure` | start MySQL |

---

## 12. Endpoint cheat sheet

Base URL `http://localhost:8080`. Everything except login needs `Authorization: Bearer <token>`.

**Authentication**

```
POST   /api/auth/login              { email, password }
GET    /api/auth/me
```

**Users** — ADMIN creates, ADMIN and PROJECT_MANAGER read

```
POST   /api/users                   { email, password, fullName, role }
GET    /api/users?role=SITE_ENGINEER&page=0&size=20
GET    /api/users/{userId}
```

**Projects**

```
POST   /api/projects                { name, description, location, startDate,
                                      expectedCompletionDate, status, managerId? }
GET    /api/projects?status=ACTIVE&page=0&size=20&sort=createdAt,desc
GET    /api/projects/{projectId}
PUT    /api/projects/{projectId}    { name, description, location, startDate,
                                      expectedCompletionDate, status }
POST   /api/projects/{projectId}/members    { userId }
GET    /api/projects/{projectId}/members
```

**Tasks**

```
POST   /api/projects/{projectId}/tasks   { title, description, priority,
                                           expectedCompletionDate, assigneeId? }
GET    /api/projects/{projectId}/tasks?status=&priority=&assigneeId=&page=&size=&sort=
GET    /api/tasks/{taskId}
PUT    /api/tasks/{taskId}               { title, description, priority,
                                           expectedCompletionDate, version }
POST   /api/tasks/{taskId}/assign        { assigneeId, version }
POST   /api/tasks/{taskId}/start         (no body)
PATCH  /api/tasks/{taskId}/progress      { progress, note?, version }
POST   /api/tasks/{taskId}/complete      (no body)
POST   /api/tasks/{taskId}/approve       (no body)
POST   /api/tasks/{taskId}/reject        { reason }
GET    /api/tasks/{taskId}/activities?page=0&size=50
```

**Task workflow**

```
TODO ──▶ IN_PROGRESS ──▶ COMPLETED ──▶ APPROVED   (end)
            ▲                    └────▶ REJECTED
            └────────────────────────────┘  (rework)
```

**Allowed values**

* project `status`: `PLANNED`, `ACTIVE`, `ON_HOLD`, `COMPLETED`, `CANCELLED`
* task `status`: `TODO`, `IN_PROGRESS`, `COMPLETED`, `APPROVED`, `REJECTED`
* `priority`: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`
* `role`: `ADMIN`, `PROJECT_MANAGER`, `SITE_ENGINEER`

---

There is also a Postman collection at
[`docs/contec-pms.postman_collection.json`](docs/contec-pms.postman_collection.json) — import it,
run **Login**, and the token, project id and task id fill themselves in as you go.
