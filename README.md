# IncidentIQ

[![CI](https://github.com/ramya5588/incident-iq/actions/workflows/ci.yml/badge.svg)](https://github.com/ramya5588/incident-iq/actions/workflows/ci.yml)

An incident management tracker with AI-assisted triage. Report incidents, work
them through to resolution as a team, and keep a full activity history of who
changed what.

## Features

- **Dashboard** with live counts (open, in progress, resolved today, SLA
  breached, AI-triaged) and the latest incidents.
- **Incident list** with search (text or incident number), status and
  priority filters, and pagination. All of it is kept in the URL, so you can
  bookmark or share a search.
- **Incident page:** edit title, description, priority and category; change
  status; assign an agent; run AI triage.
- **SLA tracking:** each incident gets a respond-by and a resolve-by deadline
  from its priority. Each one shows as on track, at risk, breached or met on
  the list and the incident page, and there's an "Overdue only" filter (see
  [SLA targets](#sla-targets)).
- **Notifications:** a bell in the sidebar with an unread count. You're
  notified when an incident is assigned to you, when someone comments on an
  incident you reported or are assigned, when the status of an incident you
  reported changes, and when an SLA deadline is missed. Missed deadlines go to
  the assignee, or to every admin if nobody is assigned. You're never notified
  about your own actions.
- **Activity timeline** on every incident: creation, status and assignee
  changes, edits, triage runs and comments, each with who did it and when.
- **Team page** with each member's role and number of active incidents.
  Admins can add members, edit names and emails, change roles, reset
  passwords, and deactivate or reactivate members there. Deactivating ends
  someone's access straight away but keeps their history, and can return
  their open incidents to the queue.
- **Account page:** change your own password. Your other signed-in devices are
  signed out.
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
| Tests | JUnit 5, MockMvc, Testcontainers (a real throwaway PostgreSQL); Vitest and React Testing Library |
| CI | GitHub Actions |

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
| Add, edit, deactivate and reactivate members; change roles; reset passwords | | | ✓ |
| Can be assigned incidents | | ✓ | ✓ |

The backend enforces these rules; the frontend only hides controls a role can't
use. The reporter, comment author and the person behind each change always come
from the signed-in session, never from the request body.

The signed-in user is re-read from the database on every request. A role
change applies on that person's next click, without signing in again. A deleted
or deactivated account, or a session that signed in before the password was
changed or reset, is signed out. Deactivated members can't sign in (they get the
same answer as a wrong password), be assigned incidents or receive
notifications. There must always be at least one active admin, and you can't
deactivate yourself.

## SLA targets

| Priority | Respond within | Resolve within |
|---|---|---|
| Critical | 15 minutes | 4 hours |
| High | 1 hour | 8 hours |
| Medium | 4 hours | 24 hours |
| Low | 8 hours | 3 days |

- **Respond** means someone first assigns the incident or moves it out of Open.
  **Resolve** means its status is set to Resolved; closing it without
  resolving also counts.
- Deadlines are measured from when the incident was reported and run around
  the clock. Changing the priority recalculates both deadlines.
- **At risk** means a quarter or less of the time is left. **Breached** means
  the deadline passed before it was done.
- Missed deadlines are checked every minute
  (`incidentiq.sla.breach-check-interval`), and each one is announced once as
  a notification. Deadlines already missed when notifications were added
  count as announced.
- Change the targets with the `incidentiq.sla.targets.<PRIORITY>.respond` and
  `.resolve` settings in
  [`application.properties`](backend/src/main/resources/application.properties).
  New targets apply to new incidents and to priority changes; deadlines that
  already exist stay as they are.

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

## Running in production

[`compose.prod.yaml`](compose.prod.yaml) runs the whole app the way it would
run on a server: PostgreSQL, the backend, and the frontend served by nginx.
nginx also forwards `/api` to the backend, so the browser talks to a single
address. Only the frontend port is published; the database and backend aren't
reachable from outside.

```bash
cp .env.example .env    # then edit .env: DB_PASSWORD and the first admin
docker compose -f compose.prod.yaml up -d --build
```

Open http://localhost:8090 (or your `HTTP_PORT`) and sign in with
`INITIAL_ADMIN_EMAIL` / `INITIAL_ADMIN_PASSWORD`.

- **Secrets** live in `.env`, which git ignores. Start from
  [`.env.example`](.env.example). If `DB_PASSWORD` is missing, startup stops
  with a clear message.
- **First admin:** on an empty database, the backend creates an admin from
  `INITIAL_ADMIN_*`. The password must be 12 to 72 characters. This only
  happens when there are no accounts at all, so the values can't be used to
  take over an existing install. After signing in, change the password on the
  **Account** page and remove `INITIAL_ADMIN_PASSWORD` from `.env`.
- **The `prod` profile** ([`application-prod.properties`](backend/src/main/resources/application-prod.properties))
  is used automatically. It marks the session cookie `Secure` and `SameSite=Lax`,
  shows only `UP`/`DOWN` on the health check, hides error details, and trusts
  `X-Forwarded-*` headers from nginx. The dev profile and its passwords are
  never active.
- **HTTPS:** put the stack behind something that handles HTTPS, such as Caddy,
  Traefik, a cloud load balancer or nginx with certificates, and forward it to
  `HTTP_PORT`. Keep `COOKIE_SECURE=true`. Browsers treat `localhost` as secure,
  so a local trial works without HTTPS. Set `COOKIE_SECURE=false` only to try
  it over plain http on another address.
- **The images** run as non-root users, include health checks, and start in
  order: the database, then the backend, then the frontend. nginx adds security
  headers (CSP, `X-Frame-Options`, `nosniff`), caches the hashed assets, and
  serves `index.html` for any app route so page reloads work.
- **Data** is in the `db-data` Docker volume. Back it up with your usual
  PostgreSQL tools (`pg_dump`). There's no built-in backup.
- **Updating:** `git pull`, then rerun the same `up -d --build`. Migrations run
  on startup.
- **Stopping:** `docker compose -f compose.prod.yaml down`. Add `-v` only if
  you want to delete the data.

## Running the tests

Docker needs to be running: the backend tests start their own throwaway
PostgreSQL container and never touch your development database.

```bash
cd backend
./mvnw test
```

Frontend tests run in a simulated browser and don't need the backend:

```bash
cd frontend
npm test            # run once
npm run test:watch  # re-run on save
npm run lint
```

### CI

GitHub Actions ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)) runs
on every push to `main` and every pull request:

- **Backend:** `./mvnw test` on Java 21.
- **Frontend:** `npm ci`, lint, tests and the production build.
- **Docker images:** builds both images and checks the nginx config and
  `compose.prod.yaml`. Nothing is pushed.

Test reports are uploaded when the backend job fails.

## Project layout

```
compose.yaml              PostgreSQL for local development
compose.prod.yaml         production-style stack (db + backend + nginx frontend)
.env.example              template for the production secrets
.github/workflows/ci.yml  CI: backend tests, frontend checks, Docker images
backend/                  Spring Boot API
  Dockerfile              production image (JRE, non-root)
  src/main/java/com/incidentiq/
    auth/                 sign-in, sessions, security rules, first/dev accounts
    incident/             incidents, timeline events, comments, stats, SLAs
    user/                 team members, roles
    ai/                   triage providers (local and OpenAI)
  src/main/resources/
    db/migration/         Flyway migrations (V1, V2, …)
    application*.properties   base, dev and prod settings
frontend/                 React single-page app
  Dockerfile, nginx.conf  production image (nginx, non-root)
  src/App.jsx             routes, sign-in state and the dashboard
  src/pages/              incidents, incident details, Team, Account, sign-in
  src/components/         Sidebar, create modal, edit form, timeline, SLA badge
  src/services/           API client (axios)
  src/test/               test setup and helpers (tests sit next to the code)
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
| `PUT /auth/password` | signed in | Change your password (`{ currentPassword, newPassword }`) |
| `GET /incidents` | signed in | List incidents (`q`, `status`, `priority`, `overdue`, `page`, `size`). `overdue=true` keeps open or in-progress incidents past a deadline. `q` searches title, description and category, or finds an incident number such as `42` or `INC-042` |
| `POST /incidents` | signed in | Report an incident |
| `GET /incidents/{id}` | signed in | Get one incident |
| `PATCH /incidents/{id}` | agent, admin | Edit title, description, priority, category |
| `PATCH /incidents/{id}/status` | agent, admin | Change status |
| `PATCH /incidents/{id}/assignee` | agent, admin | Assign or unassign |
| `POST /incidents/{id}/triage` | agent, admin | Run AI triage |
| `GET /incidents/{id}/events` | signed in | Activity timeline, newest first |
| `POST /incidents/{id}/comments` | signed in | Add a comment |
| `GET /incidents/stats` | signed in | Dashboard counts, including `slaBreached` (`resolvedSince` optional) |
| `GET /incidents/workload` | signed in | Active incidents per assignee |
| `GET /notifications` | signed in | Your 30 most recent notifications, newest first |
| `GET /notifications/unread-count` | signed in | `{ unread }` |
| `POST /notifications/{id}/read` | signed in | Mark one of your notifications read |
| `POST /notifications/read-all` | signed in | Mark all of yours read |
| `GET /users` | signed in | List team members |
| `POST /users` | admin | Add a team member |
| `PATCH /users/{id}` | admin | Edit a member's name and/or email (`{ displayName, email }`); emails stay unique |
| `POST /users/{id}/deactivate` | admin | End a member's access (`{ unassignOpenIncidents }`); returns `{ user, unassignedIncidents }` |
| `POST /users/{id}/reactivate` | admin | Restore a deactivated member |
| `PATCH /users/{id}/role` | admin | Change a member's role (`{ role }`); the last admin can't be demoted |
| `PUT /users/{id}/password` | admin | Reset a member's password (`{ newPassword }`) |

## Not built yet

- Business hours and pausing SLAs (deadlines currently run around the clock)
- Email or Slack notifications, and choosing which notifications you get
- Permanently deleting accounts, members editing their own name or email, and
  password-reset emails
- Hosting on a specific cloud provider, HTTPS certificates and automated backups
