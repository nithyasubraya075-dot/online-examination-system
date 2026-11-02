# SMARTEXAM

**Every Semester. Every Subject. One Smart Platform.**

SMARTEXAM is a Java/Spring Boot college examination portal with a responsive web UI served by the backend, MySQL persistence, role-based access, configurable academic data, server-timed MCQ exams, and backend-scored results.

## What is implemented

- Public landing page and live database-backed counts for branches, students, subjects, and exams.
- Student registration with unique email/USN checks, BCrypt password hashing, semester/branch selection, and role-aware sign-in.
- Student dashboard, semester-scoped subjects and exams, saved/resumable attempts, answer review flags, result history, and CSV export.
- Teacher accounts, subject assignments, exam scheduling/editing/deletion, four-option question management, and assigned-subject result reports.
- Admin-controlled single additional attempt per exam; teachers cannot enable repeat attempts.
- Admin branch setup (automatically creates semesters 1–8), teacher provisioning, subject configuration/teacher assignment, and student/exam/result directory screens.
- Session authentication with Spring Security, CSRF protection, role-guarded APIs, server-side ownership checks, and centralized API errors.
- Server-recorded attempt start/deadline, autosaving answers to MySQL, automatic deadline submission, and transactional server-side evaluation.
- A local-only demo seed with one CSE semester-4 subject, teacher/student accounts, and a five-question exam.

## Requirements

- Java 17 or newer (the project targets Java 17 and also runs on newer LTS JDKs).
- MySQL 8.0+.
- Windows PowerShell for the included `mvnw.cmd` wrapper. The first wrapper run downloads Apache Maven 3.9.9 from Maven Central into the current user's Maven cache. An installed Maven 3.9+ can be used instead.
- VS Code extensions: **Extension Pack for Java**, **Spring Boot Extension Pack**, and a MySQL client extension (optional).
- Internet access is needed for the first Maven dependency download and for the Google Fonts, Lucide, and Chart.js CDN assets. The HTML and app logic are served locally.

## Database and local configuration

Create a local database/user in MySQL (or use an existing local development account):

```sql
CREATE DATABASE smartexam CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'smartexam_app'@'localhost' IDENTIFIED BY 'choose-a-local-password';
GRANT ALL PRIVILEGES ON smartexam.* TO 'smartexam_app'@'localhost';
```

The default JDBC URL targets `localhost:3306/smartexam` and can create that database if the configured account has permission. Configure connection details and the first administrator through environment variables; no database or administrator password is committed:

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/smartexam?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:DB_USERNAME = "smartexam_app"
$env:DB_PASSWORD = "choose-a-local-password"
$env:SMARTEXAM_ADMIN_EMAIL = "admin@smartexam.local"
$env:SMARTEXAM_ADMIN_PASSWORD = "choose-a-unique-admin-password"
$env:SMARTEXAM_DEMO = "true"
```

`.env.example` lists the variable names. Spring Boot reads process environment variables; it does not automatically load a `.env` file.

The first startup creates the normalized tables with JPA and seeds the seven default engineering branches plus semesters 1–8 for each branch. The first admin is created only when both `SMARTEXAM_ADMIN_EMAIL` and `SMARTEXAM_ADMIN_PASSWORD` are set; existing accounts are not overwritten. `SMARTEXAM_DEMO=true` enables the local demonstration accounts and sample subject/exam. Keep demo mode off outside a local demo.

## Run

### Quick local demo without MySQL

To try the application without installing or starting MySQL, run this from the workspace root:

```powershell
.\backend\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"
```

The `demo` profile stores data in a local H2 file database at `backend/data/` and seeds the demo accounts below. This database is for local demonstration only; use the default MySQL configuration for normal deployment. The local demo admin login is `admin@smartexam.local` / `Admin@123`.

From a PowerShell terminal in the workspace:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Or, if Maven is installed:

```powershell
cd backend
mvn spring-boot:run
```

Open [http://localhost:8080](http://localhost:8080). The frontend is served by Spring Boot; there is no separate frontend server. Stop the backend with `Ctrl+C`.

To compile and run the test suite:

```powershell
cd backend
.\mvnw.cmd test
```

## Local demo accounts

Only when `SMARTEXAM_DEMO=true`:

| Role | Email | Password |
|---|---|---|
| Admin | The value configured in `SMARTEXAM_ADMIN_EMAIL` | The value configured in `SMARTEXAM_ADMIN_PASSWORD` |
| Teacher | `teacher@smartexam.local` | `Teacher@123` |
| Student | `student@smartexam.local` | `Student@123` |

These are local demonstration credentials, not production credentials. Change the demo account passwords before any shared or deployed use. The seeded demo exam is available immediately and expires 30 days after its first seed.

## Useful API routes

- `GET /api/public/stats`, `GET /api/public/branches`
- `POST /api/auth/register`, `POST /api/auth/login` (form-encoded `email` and `password`), `GET /api/auth/me`, `POST /api/auth/logout`
- Student: `/api/student/dashboard`, `/subjects`, `/exams`, `/exams/{id}/start`, `/attempts/{id}`, `/attempts/{id}/answers`, `/attempts/{id}/submit`, `/results`
- Teacher: `/api/teacher/subjects`, `/exams`, `/exams/{id}/questions`, `/results`
- Admin: `/api/admin/summary`, `/branches`, `/subjects`, `/students`, `/teachers`, `/exams`, `/results`

Mutating browser requests include the CSRF token obtained from `GET /api/auth/csrf`. Student result and exam endpoints use the authenticated student's profile and never return correct answers. Teacher question APIs return correct options only for subjects assigned to that teacher. Admin APIs are restricted to the admin role.

## Notes and limitations

- This is a runnable college-project baseline, not a production-hardened examination service. Use HTTPS, secrets management, database migrations, backups, audit logging, rate limiting, and operational monitoring before deployment.
- Hibernate `ddl-auto=update` is convenient for local setup; production deployments should use versioned schema migrations (for example Flyway) and disable automatic schema changes.
- Student records are self-registered; administrator screens currently list students and teachers but do not include account-edit/delete workflows. Admins can add branches, teachers, and subjects, and assign teachers through subject configuration.
- Teachers can edit or delete exams/questions only before any student attempt is recorded. Exam windows are enforced on the server. Exam results are CSV-downloadable from the results screen; generated PDF reports are not included.
- Scheduled emails, proctoring, file uploads, password reset, and deployment automation are not implemented.
