# IncidentIQ

[![CI](https://github.com/ramya5588/incident-iq/actions/workflows/ci.yml/badge.svg)](https://github.com/ramya5588/incident-iq/actions/workflows/ci.yml)

An incident management tracker with AI-assisted triage. Report incidents, work
them through to resolution as a team, and keep a full activity history of who
changed what.

## Features

- **Dashboard** with live counts (open, in progress, resolved today, SLA
  breached, AI-triaged) and the latest incidents, plus:
  - **My work** (agents and admins):
    - your open and in-progress incidents, the one due soonest first
    - your incidents on hold, with the reason and how long they've waited
    - the unassigned queue, with **Take it**
    - incidents with unread notifications for you
  - **Open incidents, last 30 days:** how many were still open at the end of
    each day (agents and admins; hover or use the arrow keys, or show a table).
  - **Your incidents** (reporters): what you reported, with the ones waiting
    on your reply flagged.
- **Reports** (agents and admins), for the last 7, 30 or 90 days or a custom
  range, optionally for one priority:
  - **Headline numbers:** incidents reported and resolved, median time to
    first response and to resolve, the share of response and resolution SLAs
    met, how many are overdue now, and satisfaction (the average rating given
    in the period, and the share of 4s and 5s). Duplicates are left out of
    every number, so one problem reported three times counts once.
  - **Charts and tables:** reported vs resolved per day (hover, arrow keys or
    a table), SLA met vs missed by priority, top categories, and current open
    work per assignee.
  - **Download CSV** gives the range's incidents with their deadlines,
    whether each was met, and the reporter's rating and feedback.
- **Incident list** with search (text or incident number), status and
  priority filters, and pagination. All of it is kept in the URL, so you can
  bookmark or share a search.
- **Duplicates** (agents and admins): "Mark as duplicate…" closes an incident
  as a duplicate of another, the main one. Its reporter and watchers follow the
  main incident from then on, and the merge can be undone (see
  [Duplicates](#duplicates)).
- **Saved replies** (agents and admins): reusable text inserted into a
  comment or internal note with one click from "Saved replies" next to the
  comment box. `{reporter}`, `{incident}` and `{agent}` are filled in. Admins
  manage replies shared with the team; everyone can keep personal ones (see
  [Saved replies](#saved-replies)).
- **Satisfaction ratings:** once an incident is resolved or closed, its
  reporter rates how it was handled from 1 to 5 stars, with an optional
  comment, and can change it later. The team sees the rating on the incident
  and its timeline, and Reports shows the average.
- **Watching:** anyone can Watch an incident to get its comments, status
  changes and missed deadlines, and use "Watching only" on the list to see
  what they follow. People you @mention start watching automatically (see
  [Watching](#watching)).
- **Bulk changes** (agents and admins): tick incidents on the list, or "select
  all" on the page, then change their status (including On hold, with a
  reason), priority or assignee in one go. Each incident is changed exactly as
  a single change would, so the timeline, notifications and SLA clocks stay
  right. If some can't be changed, the others still are, and you're told which
  failed and why.
- **Incident page:** edit title, description, priority and category; change
  status; assign an agent; run AI triage.
- **Attachments:** add screenshots, PDFs and text or log files when reporting
  an incident or later on its page, up to 5 MB each and 10 per incident.
  Images show as thumbnails; other files download. Adding and removing files
  shows on the timeline (see [Attachments](#attachments)).
- **On hold:** put an incident on hold while you wait on the reporter, a third
  party or a scheduled time. You pick a reason and can add a note, and both
  show on the timeline. The resolution clock pauses while it's on hold. If
  you're waiting on the reporter, their next comment puts it back in progress
  and tells the assignee.
- **SLA tracking:** each incident gets a respond-by and a resolve-by deadline
  from its priority. Each one shows as on track, at risk, breached, met or
  paused on the list and the incident page, and there's an "Overdue only" filter (see
  [SLA targets](#sla-targets)). Admins can make chosen priorities count
  business hours only, with holidays (see [Business hours](#business-hours)).
- **Notifications:** a bell in the sidebar with an unread count. You're
  notified when an incident is assigned to you, when someone @mentions you,
  when someone comments on an incident you reported, are assigned or watch,
  when the status of an incident you reported or watch changes, and when an
  SLA deadline is missed. Missed deadlines go to
  the assignee, or to every admin if nobody is assigned. You're never notified
  about your own actions.
- **Email notifications:** on the Account page, each person chooses which
  notifications they also get by email, with a link to the incident. By
  default assignments, mentions and missed deadlines are emailed; comments and
  status changes are not. Emails are sent in the background after a change is saved,
  so a slow or broken mail server never slows the app or loses a change (see
  [Email](#email)).
- **Activity timeline** on every incident: creation, status and assignee
  changes, edits, triage runs and comments, each with who did it and when.
- **Internal notes:** agents and admins choose between a reply, which the
  reporter sees, and an internal note, which only agents and admins see.
  Notes are shown in amber with an "Internal" label, and the comment box turns
  amber while you write one (see [Internal notes](#internal-notes)).
- **@mentions:** type `@` in a comment or internal note to pick a teammate
  from a list (arrow keys and Enter, or click). They're notified, and the
  mention is highlighted on the timeline, more strongly when it's you (see
  [Mentions](#mentions)).
- **Team page** with each member's role and number of active incidents.
  Admins can add members, edit names and emails, change roles, reset
  passwords, and deactivate or reactivate members there. Deactivating ends
  someone's access straight away but keeps their history, and can return
  their open incidents to the queue.
- **Account page:** change your own name, sign-in email and password. A new
  email needs your password and, when email is set up, a click on a link sent
  to the new address (see [Changing your email](#changing-your-email)).
  Changing your password signs out your other devices. Only admins change
  roles.
- **Sign-in and roles:** session-based sign-in. Reporters, agents and admins
  each get different permissions (see [Roles](#roles)).
- **Forgot password?** When email is set up, the sign-in page offers a reset
  link by email, and admins can email a member a link from the Team page
  instead of choosing a password for them (see
  [Password reset](#password-reset)).
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

**1. Start PostgreSQL and Mailpit.** PostgreSQL runs on port 5433, so it
won't clash with a local Postgres. Mailpit catches the app's emails; read them
at http://localhost:8025.

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
| Add and see internal notes | | ✓ | ✓ |
| Edit details, change status, assign, run AI triage, bulk changes | | ✓ | ✓ |
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
  the clock, unless [business hours](#business-hours) are on for that
  priority. Changing the priority recalculates both deadlines.
- **On hold pauses the resolve deadline:** time spent on hold is added to it
  when work resumes, and every hold counts. Paused incidents aren't overdue
  and don't trigger missed-deadline alerts. A deadline that was already
  missed before the hold stays missed. Putting an incident on hold also
  counts as responding to it.
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

### Business hours

Admins set these at the bottom of the **Team** page; everyone else sees a
one-line summary there. They're off until an admin turns them on.

- **What counts:** working hours (for example 09:00–17:00) on chosen working
  days in one time zone, except listed holidays. Local times follow the time
  zone's daylight saving rules.
- **Per priority:** pick which priorities use business hours. The others keep
  running around the clock. Critical, for example, is often left around the
  clock.
- **Example:** a High incident reported at 16:30 on a Friday with
  Mon–Fri 09:00–17:00 must be picked up by 09:30 on Monday (30 minutes on
  Friday, 30 on Monday), or Tuesday if Monday is a holiday.
- **On hold** adds only the business time it was held, so a weekend on hold
  adds nothing.
- **At risk** uses business time too: a quarter or less of the counted time
  left. Badges show "time left (business hours)" as of when the page loaded,
  and the incident page marks those deadlines "business hours".
- **Overdue and alerts** don't change: deadlines are stored as exact times, so
  the overdue filter, dashboard count and missed-deadline alerts work as
  before.
- **Changes** apply to deadlines set from then on (new incidents, priority
  changes, coming off hold). Existing deadlines stay as they are, as with
  target changes.

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

Email settings are in [Email](#email).

Docker Compose reads `POSTGRES_PASSWORD` (default `incidentiq_dev`),
`POSTGRES_PORT` (default `5433`), and `MAILPIT_SMTP_PORT` / `MAILPIT_WEB_PORT`
(defaults `1025` / `8025`).

## Email

Notification emails are sent only when both `MAIL_HOST` and `MAIL_FROM` are
set. Otherwise notifications stay in the app, and the Account page says email
isn't set up.

| Variable | Default | Purpose |
|---|---|---|
| `MAIL_HOST` | *(none)* | SMTP server, e.g. your provider's `smtp.example.com`. |
| `MAIL_PORT` | `587` | SMTP port. |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | *(none)* | SMTP sign-in. |
| `MAIL_FROM` | *(none)* | Sender, e.g. `IncidentIQ <incidents@example.com>`. |
| `MAIL_SMTP_AUTH` / `MAIL_STARTTLS` | `true` / `true` | Turn off for servers without sign-in or TLS. |
| `APP_URL` | `http://localhost:5173` | Where links in emails point: the address people open the app at. |

- **In development** the `dev` profile sends to Mailpit (`localhost:1025`),
  which `docker compose up -d` starts. Nothing is delivered anywhere; open
  http://localhost:8025 to read what was sent. If you run the frontend on
  another port, set `APP_URL` so the links match.
- **Sending** happens after the change is committed, on a small background
  pool, with 10-second timeouts. A failed send is logged as a warning; the
  in-app notification is already saved. Deactivated members get no email.
- **The health check ignores email**, so a mail server that's down never makes
  the app report itself unhealthy.

### Password reset

With email set up, "Forgot password?" on the sign-in page emails a link to
choose a new password. Without email, the link isn't shown and admins reset
passwords on the Team page as before.

- **Same answer for everyone:** asking for a link always says "If an account
  with that email exists, we've sent it a link", so it can't be used to find
  out who has an account. Deactivated accounts never get one.
- **Links** hold a random 256-bit token, work once and expire after 30 minutes
  (`incidentiq.auth.password-reset-link-lifetime`). Only a SHA-256 hash of the
  token is stored. The token sits after `#` in the link, so browsers never send
  it to a server or write it to access logs, and the page removes it from the
  address bar.
- **Limits:** a new link cancels the older ones, and an account can ask for 3
  links an hour; more requests get the same answer but send nothing. If two
  requests try to use one link at once, only one succeeds.
- **After a reset** every other link is cancelled, other signed-in sessions are
  signed out on their next request, and the person gets a "your password was
  changed" email.
- **Admins** can use **Email a reset link** in a member's Reset password panel,
  so the member chooses their own password and no one has to pass one on.

### Changing your email

Your email is how you sign in, so changing it on the Account page takes your
current password.

- **With email set up,** nothing changes until you click the link sent to the
  new address, so a typo can't lock you out. The page shows the waiting change
  and lets you cancel it. Links work once, expire after 24 hours
  (`incidentiq.auth.email-change-link-lifetime`), and work whether or not
  you're signed in. Asking again replaces the earlier link, and you can ask 3
  times an hour.
- **When it's confirmed,** your old address gets a "your email was changed"
  notice in case it wasn't you. Your session carries on under the new email.
- **Without email,** the change applies straight away after the password check.
- **Rules:** emails stay unique regardless of upper or lower case, checked again
  at confirmation in case someone took the address meanwhile. Deactivating
  someone voids their open link.

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

- **Email:** set `MAIL_*` and `APP_URL` in `.env` (see [Email](#email)).
  Leave `MAIL_HOST` empty to keep notifications in-app only.
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
- **Data** is in the `db-data` Docker volume, attachments included. Back it
  up with your usual PostgreSQL tools (`pg_dump`). There's no built-in backup.
- **Uploads:** nginx accepts request bodies up to 6 MB (`client_max_body_size`
  in [`nginx.conf`](frontend/nginx.conf)), enough for one 5 MB attachment.
  Any proxy in front of the stack must allow at least that too.
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
| `PATCH /auth/profile` | signed in | Change your name (`{ displayName }`) |
| `POST /auth/email-change` | signed in | Change your email (`{ newEmail, currentPassword }`); returns `{ user, pending }`, where `pending` is null if it applied straight away |
| `GET /auth/email-change` | signed in | Your change waiting for confirmation (`{ newEmail, expiresAt }`), or 204 |
| `DELETE /auth/email-change` | signed in | Cancel it |
| `POST /auth/email-change/confirm` | anyone | Confirm from the emailed link (`{ token }`); returns `{ email }` |
| `GET /auth/password-reset` | anyone | `{ available }`: whether reset links can be emailed |
| `POST /auth/password-reset` | anyone | Email a reset link (`{ email }`); always 202 with the same message |
| `POST /auth/password-reset/check` | anyone | 204 if a link (`{ token }`) can still be used, 400 if not |
| `POST /auth/password-reset/confirm` | anyone | Set a new password (`{ token, newPassword }`) |
| `GET /incidents` | signed in | List incidents (`q`, `status`, `priority`, `overdue`, `page`, `size`). `overdue=true` keeps open or in-progress incidents past a deadline. `q` searches title, description and category, or finds an incident number such as `42` or `INC-042` |
| `POST /incidents` | signed in | Report an incident |
| `GET /incidents/{id}` | signed in | Get one incident |
| `GET /dashboard/my-work` | agent, admin | `{ dueSoon, waiting, unassigned, unassignedTotal, unread }` for the signed-in person |
| `GET /dashboard/my-incidents` | signed in | The incidents you reported, most recently updated first |
| `GET /reports/open-trend?days=30&timeZone=` | agent, admin | Open incidents at the end of each day (up to 90 days; today's is now) |
| `PUT /incidents/{id}/duplicate-of` | agent, admin | Close it as a duplicate: `{ mainIncidentId }` |
| `DELETE /incidents/{id}/duplicate-of` | agent, admin | Undo that: no longer a duplicate, and reopened |
| `GET /incidents/{id}/duplicates` | signed in | The incidents merged into this one |
| `GET /saved-replies` | agent, admin | Shared replies, then your personal ones; each says whether you can edit it |
| `POST /saved-replies` | agent, admin | Add one: `{ title, body, personal }`. Only admins can add shared ones (`personal: false`) |
| `PUT /saved-replies/{id}` | agent, admin | Change the title and text: admins for shared replies, the owner for personal ones |
| `DELETE /saved-replies/{id}` | agent, admin | Delete it (same rule) |
| `PUT /incidents/{id}/rating` | the reporter | Rate a resolved or closed incident: `{ score: 1-5, comment? }`. Rating again replaces it |
| `GET /incidents/{id}/watchers` | signed in | `{ watching, count, watchers }`; `watchers` (names) only for agents and admins |
| `PUT /incidents/{id}/watchers/me` | signed in | Start watching (does nothing if you already are) |
| `DELETE /incidents/{id}/watchers/me` | signed in | Stop watching |
| `PATCH /incidents/bulk` | agent, admin | The same change for up to 100 incidents: `{ incidentIds, status?, holdReason?, holdNote?, priority?, assigneeId?, unassign? }`. Returns `{ updated, failed: [{ incidentId, message }] }` |
| `PATCH /incidents/{id}` | agent, admin | Edit title, description, priority, category |
| `PATCH /incidents/{id}/status` | agent, admin | Change status (`{ status }`; for `ON_HOLD` also `holdReason` and an optional `holdNote`) |
| `PATCH /incidents/{id}/assignee` | agent, admin | Assign or unassign |
| `POST /incidents/{id}/triage` | agent, admin | Run AI triage |
| `GET /incidents/{id}/events` | signed in | Activity timeline, newest first; internal notes only for agents and admins |
| `POST /incidents/{id}/comments` | signed in | Add a comment; `"internal": true` makes it an internal note (agents and admins only). Mention someone with `@[Name](user-id)` |
| `GET /incidents/{id}/attachments` | signed in | The incident's attachments (names, sizes, who added them; no file data) |
| `POST /incidents/{id}/attachments` | signed in | Attach one file (multipart field `file`); reporters only on their own incidents |
| `GET /incidents/{id}/attachments/{attachmentId}` | signed in | The file: images inline, everything else as a download |
| `DELETE /incidents/{id}/attachments/{attachmentId}` | signed in | Remove it (whoever added it, or an admin) |
| `GET /incidents/stats` | signed in | Dashboard counts, including `slaBreached` (`resolvedSince` optional) |
| `GET /incidents/workload` | signed in | Active incidents per assignee |
| `GET /reports/summary` | agent, admin | Report for `from`–`to` (inclusive dates) in `timeZone` (an IANA name, default UTC), optionally one `priority`; at most 366 days |
| `GET /reports/incidents.csv` | agent, admin | The same range's incidents as CSV |
| `GET /notifications` | signed in | Your 30 most recent notifications, newest first |
| `GET /notifications/unread-count` | signed in | `{ unread }` |
| `POST /notifications/{id}/read` | signed in | Mark one of your notifications read |
| `POST /notifications/read-all` | signed in | Mark all of yours read |
| `GET /notifications/email-preferences` | signed in | Which notifications you get by email (`assigned`, `commented`, `statusChanged`, `slaBreached`) and `emailEnabled` |
| `PUT /notifications/email-preferences` | signed in | Save your choices (all four, as true/false) |
| `GET /users` | signed in | List team members |
| `POST /users` | admin | Add a team member |
| `PATCH /users/{id}` | admin | Edit a member's name and/or email (`{ displayName, email }`); emails stay unique |
| `POST /users/{id}/deactivate` | admin | End a member's access (`{ unassignOpenIncidents }`); returns `{ user, unassignedIncidents }` |
| `POST /users/{id}/reactivate` | admin | Restore a deactivated member |
| `PATCH /users/{id}/role` | admin | Change a member's role (`{ role }`); the last admin can't be demoted |
| `PUT /users/{id}/password` | admin | Reset a member's password (`{ newPassword }`) |
| `POST /users/{id}/password-reset-link` | admin | Email a member a reset link |
| `GET /sla/business-hours` | signed in | SLA business hours: `{ enabled, timeZone, dayStart, dayEnd, workingDays, priorities, holidays }` |
| `PUT /sla/business-hours` | admin | Save them (same shape; times as `HH:mm`, holidays as `{ date, name }`) |

### How reports count

- **Days** are calendar days in the viewer's time zone, sent by the browser.
- **Reported in the range** means created between the start of the first day
  and the end of the last.
- **Medians:**
  - first response covers incidents reported in the range that have been
    picked up
  - time to resolve covers incidents *resolved* in the range, wherever they
    were reported
  - both are calendar time, not business hours
- **SLA met / missed** counts incidents reported in the range whose deadline
  is decided. Ones still running or on hold aren't counted until they're done
  or missed. Outcomes come from the stored deadlines, so business hours and
  holds are already taken into account.
- **Open work** is the current state and isn't limited to the date range; the
  priority filter applies to it.
- **The CSV** is UTF-8 for Excel, with times in the viewer's time zone. Text
  that starts like a spreadsheet formula (`=`, `+`, `-`, `@`) is prefixed with
  `'` so opening the file can't run it.

## Internal notes

- **Who sees them:** only agents and admins. The server leaves them out of the
  timeline it sends to reporters, so they never reach a reporter's browser.
  Reporters can't add them (403).
- **Who's told:** the incident's reporter and assignee, but only if they're
  agents or admins. A reporter with the User role is never notified, and the
  notification only says a note was added, not what it says.
- **No side effects:** a note doesn't count as a reply. It doesn't take an
  incident off "waiting on the reporter" hold, even if an agent reported the
  incident themselves.
- **Can't change later:** a note stays internal. To tell the reporter
  something, post a reply.

## Duplicates

- **Marking one:** on the duplicate, click **Mark as duplicate…** and enter the
  main incident's number (INC-009 or just 9). You confirm before anything
  changes.
- **What happens:**
  - The duplicate is closed and links to the main incident.
  - Both timelines record the merge.
  - The duplicate's reporter and watchers start watching the main incident and
    are told where updates continue.
  - The main incident lists its merged duplicates.
- **Not allowed:** an incident can't be a duplicate of itself, of an incident
  that's itself a duplicate, or become a duplicate while others are merged into
  it. So there are no chains or loops.
- **Undo:** **Undo** on the duplicate reopens it. People who started watching
  the main incident keep watching it, and can stop with **Watch**.
- **Elsewhere:** the incident list shows a "Duplicate" badge. Reports and the
  CSV export leave duplicates out.

## Saved replies

- **Where:** manage them on the **Account** page; use them from **Saved
  replies** next to the comment box on an incident. Picking one inserts it at
  the cursor, so you can edit it before posting, as a reply or an internal note.
- **Placeholders:** `{reporter}` (the reporter's name), `{incident}` (for
  example INC-012) and `{agent}` (your name) are filled in when inserted.
- **Shared and personal:** admins add, change and delete replies shared with
  the whole team. Agents and admins can each keep up to 50 personal replies
  that nobody else sees, not even admins.
- **Who:** agents and admins only. Reporters never see the button or the API.

## Watching

- **Who:** anyone signed in can watch any incident. The page shows how many
  people watch it. Agents and admins also see who; reporters see only the
  count.
- **What watchers get:** the incident's comments, status changes and missed
  SLA deadlines, through the usual bell and email settings. They don't get
  assignment notices; those go to the person assigned.
- **Privacy:** watchers with the User role never hear about internal notes.
  Deactivated members get nothing.
- **One notification each:** if you're also the reporter, the assignee or
  mentioned, you still get one notification per event.
- **Automatic watching:** when someone @mentions you, you start watching, so
  you hear the replies. Mentioning yourself doesn't. You can stop watching at
  any time.
- **List filter:** "Watching only" on the incident list (`?watching=true`)
  shows just the incidents you follow.

## Mentions

- **Who can be mentioned:** active agents and admins, from any comment or
  note. The incident's own reporter can be mentioned in a reply, but never in
  an internal note. Reporters can't mention other reporters. The server checks
  every mention, so these rules hold even for API calls.
- **How they're stored:** as `@[Name](user-id)`, so a mention keeps pointing at
  the right person. The server always writes in the person's real name. A
  mention someone isn't allowed to make, or of an unknown or deactivated
  person, is saved as plain text and notifies nobody.
- **Notifications:** "Alex mentioned you on INC-012 …", or "… in an internal
  note on …". Someone who'd also be told about the comment, such as the
  assignee, gets just the mention. Mentioning yourself notifies nobody. Up to
  20 people per comment.
- **Email:** mentions are emailed by default. Turn this off under "Someone
  mentions me" on the Account page.
- **Length:** the 2,000-character limit counts a mention as `@Name`, as you see
  it.

## Attachments

- **Who:** anyone signed in can see an incident's attachments, as they can
  the incident. Agents and admins can attach to any incident; reporters only to
  incidents they reported. Whoever added a file, or an admin, can remove it.
- **What:** images (PNG, JPEG, GIF, WebP), PDFs, and text files (`.txt`,
  `.log`, `.csv`, `.json`); 5 MB each, 10 per incident.
- **Type checks:** the type comes from the file's first bytes, not its name or
  the type the browser claims. A file named `.png` that isn't a PNG is refused.
  Text files must be clean UTF-8, and they're always served as plain text.
  HTML, SVG and anything else are refused, so nothing uploaded can run as a
  page in the app.
- **Serving:** images are served inline (for thumbnails) and everything else
  as a download. Every file is served with a locked-down
  Content-Security-Policy (`sandbox`, no scripts) and `nosniff`. File names are
  cleaned of folders and control characters.
- **Storage:** files are kept in PostgreSQL, so backups and the Docker setup
  need nothing extra. That's fine for screenshots and logs. If attachments
  grow into many gigabytes, moving them to object storage (S3 or similar)
  would keep the database small.

## Not built yet

- Slack or other chat notifications, and HTML or digest emails
- Permanently deleting accounts
- Hosting on a specific cloud provider, HTTPS certificates and automated backups
