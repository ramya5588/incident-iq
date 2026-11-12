# IncidentIQ

An incident management tracker with AI-assisted triage. Report incidents, work
them through to resolution as a team, and keep a full activity history of who
changed what.

## Features

- **Dashboard** with live counts (open, in progress, resolved today, AI-triaged)
  and the latest incidents.
- **Incident list** with search (text or incident number), status and
  priority filters, and pagination. All of it is kept in the URL, so you can
  bookmark or share a search.
- **Incident page:** edit title, description, priority and category; change
  status; assign an agent; run AI triage.
- **Activity timeline** on every incident: creation, status and assignee
  changes, edits, triage runs and comments, each with who did it and when.
- **Team page** with each member's role and number of active incidents.
- **Sign-in and roles:** session-based sign-in. Reporters, agents and admins
  each get different permissions (see [Roles](#roles)).
- **AI triage:** suggests priority and category and writes a summary. A local
  rule-based provider is the default; OpenAI is optional.

## Tech stack

| Part | Technology |
|---|---|
| Backend | Java 21, Spring Boot 4, Spring Security, Spring Data JPA, Flyway |
| Database | PostgreSQL 17, run with Docker Compose |
| Frontend | React 19, Vite, React Router, axios |
| Tests | JUnit 5, MockMvc, Testcontainers (a real throwaway PostgreSQL) |

## Prerequisites

- **Java 21.** Maven isn't needed; the project includes the Maven wrapper.
- **Node.js 20 or newer**, with npm.
- **Docker** (Docker Desktop on Windows and macOS). It runs the database and the
  backend tests.

## Quick start

Run these from the repository root. You'll need three terminals: the backend
and the frontend each keep running.

**1. Start PostgreSQL** (on port 5433, so it won't clash with a local Postgres):

```bash
docker compose up -d
```

**2. Start the backend** (on port 8080). The first start creates the tables.

```bash
cd backend
DB_PASSWORD=incidentiq_dev SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

In Windows PowerShell:

```powershell
cd backend
$env:DB_PASSWORD = "incidentiq_dev"; $env:SPRING_PROFILES_ACTIVE = "dev"; .\mvnw.cmd spring-boot:run
```

**3. Start the frontend** (on port 5173):

```bash
cd frontend
npm install
npm run dev
```

**4. Open http://localhost:5173 and sign in** (see below).

The frontend sends `/api` requests to the backend on port 8080, so you don't
need any CORS or URL settings.

## Signing in locally

The **`dev` profile** makes a local database usable right away. It only runs
when `SPRING_PROFILES_ACTIVE=dev` is set:

- **On an empty database**, it creates a first admin, `admin@incidentiq.test`.
- **Any account without a password** gets the default development password.
- **Accounts that already have a password** are never changed.

The default password and the admin email are in
[`backend/src/main/resources/application-dev.properties`](backend/src/main/resources/application-dev.properties).
Sign in as the admin, then add team members on the **Team** page. Each new
member needs a first password of 8 to 72 characters.

> **Never enable the `dev` profile in production.** It sets a known password.

Once every account has a password, you can start the backend without the dev
profile.

## Roles

| | User (reporter) | Agent | Admin |
|---|---|---|---|
| View incidents, report new ones, comment | ✓ | ✓ | ✓ |
| Edit details, change status, assign, run AI triage | | ✓ | ✓ |
| Add team members | | | ✓ |
| Can be assigned incidents | | ✓ | ✓ |

The backend enforces these rules; the frontend only hides controls a role can't
use. The reporter, comment author and the person behind each change always come
from the signed-in session, never from the request body.

## Configuration

The backend reads these environment variables:

| Variable | Default | Purpose |
|---|---|---|
| `DB_PASSWORD` | *(required)* | Database password. It's `incidentiq_dev` for the Docker Compose database. |
| `DB_URL` | `jdbc:postgresql://localhost:5433/incidentiq` | JDBC URL. |
| `DB_USERNAME` | `incidentiq` | Database user. |
| `SPRING_PROFILES_ACTIVE` | *(none)* | Set to `dev` for local development (see above). |
| `AI_PROVIDER` | `local` | `local` (rule-based, no network) or `openai`. |
| `OPENAI_API_KEY` | *(none)* | Required when `AI_PROVIDER=openai`. |
| `OPENAI_MODEL` | `gpt-6-astra` | Model used when `AI_PROVIDER=openai`. |

Docker Compose reads `POSTGRES_PASSWORD` (default `incidentiq_dev`) and
`POSTGRES_PORT` (default `5433`).

## Running the tests

Docker needs to be running: the backend tests start their own throwaway
PostgreSQL container and never touch your development database.

```bash
cd backend
./mvnw test
```

Lint the frontend:

```bash
cd frontend
npm run lint
```

## Project layout

```
compose.yaml              PostgreSQL for local development
backend/                  Spring Boot API
  src/main/java/com/incidentiq/
    auth/                 sign-in, sessions, security rules, dev accounts
    incident/             incidents, timeline events, comments, stats
    user/                 team members
    ai/                   triage providers (local and OpenAI)
  src/main/resources/
    db/migration/         Flyway migrations (V1, V2, …)
    application*.properties
frontend/                 React single-page app
  src/App.jsx             routes, sign-in state and the dashboard
  src/pages/              incident list, incident details, Team, sign-in
  src/components/         Sidebar, create modal, edit form, timeline
  src/services/           API client (axios)
```

## API overview

All endpoints are under `/api`. Every one needs a signed-in session, except
sign-in and the "who am I" check. Requests that change data also need the CSRF
token: the backend sets an `XSRF-TOKEN` cookie, and the client sends it back in
an `X-XSRF-TOKEN` header. axios does this automatically.

| Method & path | Who | What |
|---|---|---|
| `POST /auth/login` | anyone | Sign in with `{ email, password }` |
| `POST /auth/logout` | signed in | Sign out |
| `GET /auth/me` | anyone | The signed-in user, or 401 |
| `GET /incidents` | signed in | List incidents (`q`, `status`, `priority`, `page`, `size`). `q` searches title, description and category, or finds an incident number such as `42` or `INC-042` |
| `POST /incidents` | signed in | Report an incident |
| `GET /incidents/{id}` | signed in | Get one incident |
| `PATCH /incidents/{id}` | agent, admin | Edit title, description, priority, category |
| `PATCH /incidents/{id}/status` | agent, admin | Change status |
| `PATCH /incidents/{id}/assignee` | agent, admin | Assign or unassign |
| `POST /incidents/{id}/triage` | agent, admin | Run AI triage |
| `GET /incidents/{id}/events` | signed in | Activity timeline, newest first |
| `POST /incidents/{id}/comments` | signed in | Add a comment |
| `GET /incidents/stats` | signed in | Dashboard counts (`resolvedSince` optional) |
| `GET /incidents/workload` | signed in | Active incidents per assignee |
| `GET /users` | signed in | List team members |
| `POST /users` | admin | Add a team member |

## Not built yet

- SLA and response-time tracking
- Notifications
- Changing your password, and changing a member's role
- Frontend tests, CI and a production deployment setup
