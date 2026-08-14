# Nirikshan Backend

Spring Boot 3.3 / Java 17 backend for the Nirikshan crowd-risk prototype. The Python CV pipeline supplies pre-computed density, movement, risk level, and explanation values; this service stores, broadcasts, and surfaces them.

## Authentication roles

The public mobile experience at `/alerts` creates `CITIZEN` accounts only. Security accounts are created by an administrator and must change their one-time password at first sign-in. The unlinked administrator console is `/console`, with login at `/console/login`.

Before starting the backend for the first time, seed a demo admin using environment variables:

```powershell
$env:ADMIN_SEED_EMAIL="admin@example.com"
$env:ADMIN_SEED_PASSWORD="ChangeThisPassword123!"
$env:NIRIKSHAN_JWT_SECRET="use-a-long-random-secret-of-at-least-32-characters"
```

The hidden route is suitable only for a hackathon demo. A production deployment also needs network-level administrative access restrictions.

## Run locally

Requirements: Java 17+, Maven 3.9+.

The CV upload runner requires Python 3.10 and the project virtual environment. From `cv-pipeline/` on Windows PowerShell:

```powershell
python -m venv venv
.\venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
pip install torch torchvision --index-url https://download.pytorch.org/whl/cu121
pip install -r requirements.txt
[Environment]::SetEnvironmentVariable("NIRIKSHAN_PYTHON", "D:\\Nirikshan\\cv-pipeline\\venv\\Scripts\\python.exe", "User")
$env:NIRIKSHAN_PYTHON = "D:\\Nirikshan\\cv-pipeline\\venv\\Scripts\\python.exe"
```

Direct CV commands should be run after activating this venv. The backend job runner resolves `NIRIKSHAN_PYTHON` when it starts and logs the actual executable path. If the variable is missing, it logs a warning and falls back to system `python`.

Annotated videos are converted to H.264 for browser playback. Install FFmpeg and place `ffmpeg.exe` on PATH if the admin page must play the generated MP4 directly.

If `torch.cuda.is_available()` returns `False`, CUDA is unavailable to that interpreter; the CV pipeline will report `Using device: cpu (no GPU detected)` and use slower CPU inference.

```text
mvn spring-boot:run
```

On Windows, the recommended launcher sets the CV venv variables in the same process that starts Spring Boot:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\run-backend.ps1
```

### Database profiles

The default profile is `local-postgres`, which uses PostgreSQL with Flyway migrations and Hibernate schema validation. This keeps risk events, citizen reports, alerts, jobs, and users across backend restarts. The PostgreSQL JDBC driver and Flyway are included in `pom.xml`.

For a local PostgreSQL server:

```powershell
$env:SPRING_PROFILES_ACTIVE="local-postgres"
$env:DATABASE_URL="jdbc:postgresql://localhost:5432/nirikshan"
$env:DATABASE_USERNAME="postgres"
$env:DATABASE_PASSWORD="your-local-password"
mvn spring-boot:run
```

`DATABASE_URL` may also be a Railway-style `postgresql://...` URL. The backend converts it to the JDBC form automatically. `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` override the `DATABASE_*` variables when present.

For Railway deployment, attach a Railway PostgreSQL service and set the backend service variables from the database service. The usual Railway URL is shaped like this (keep the real credential in Railway variables, never in source control):

```text
SPRING_PROFILES_ACTIVE=prod
DATABASE_URL=postgresql://postgres:<password>@postgres.railway.internal:5432/railway
NIRIKSHAN_JWT_SECRET=<long-random-secret>
```

Railway may alternatively expose `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, and `PGPASSWORD`; the `prod` profile supports those variables too. For local testing against the Railway database, use the database service's reachable/public URL if `postgres.railway.internal` is only resolvable inside Railway:

```powershell
$env:SPRING_PROFILES_ACTIVE="prod"
$env:DATABASE_URL="postgresql://postgres:<password>@<railway-public-host>:<port>/railway"
mvn spring-boot:run
```

On a fresh PostgreSQL database, Flyway applies `src/main/resources/db/migration/V1__create_initial_schema.sql` before Hibernate starts. Existing non-empty prototype databases are baselined at version `0`, then the idempotent migration runs; it can create missing tables such as `users` without deleting existing data. If the schema is incomplete in another way, startup fails with the exact validation error instead of silently serving a broken app. The seed runner is enabled only for `dev` and `local-postgres`; production does not create demo data or admin accounts.

The public health endpoint is `http://localhost:8080/api/health`. It reports the active profile, database name/schema, missing required tables, and row counts. A `DOWN` response means the backend is pointed at the wrong database, cannot connect, or has an incomplete schema.

To use the disposable offline H2 fallback:

```powershell
$env:SPRING_PROFILES_ACTIVE="dev"
mvn spring-boot:run
```

The H2 `dev` profile uses `create-drop`; data is intentionally lost when the process stops. H2 console: `http://localhost:8080/h2-console` with JDBC URL `jdbc:h2:mem:nirikshan`.

For a production admin, create the account in the connected PostgreSQL database. `pgcrypto` produces BCrypt-compatible `$2a$` hashes accepted by Spring Security:

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;
UPDATE users
SET password_hash = crypt('replace-with-a-strong-password', gen_salt('bf', 12)),
    role = 'ADMIN', active = true, must_change_password = false
WHERE lower(email) = lower('admin@example.com');
```

Always run this against the same database shown by `/api/health`.

### Continuous camera coverage

The admin Video ingestion page treats each campus zone as a security camera. An administrator connects a pre-recorded video file to a zone, and the backend starts a persistent `ZoneFeed` that loops the footage from frame 0 until coverage is stopped. Loop-mode processing emits one risk event approximately every real second and re-bases each event timestamp to the current wall-clock time, so the dashboard, map, heatmap, zone cards, and trend chart always look current rather than replaying stale video timestamps.

This is a demonstration simulator, not a production camera ingest path. In production, the same per-second processing and WebSocket event pipeline would consume frames from an actual RTSP/CCTV stream instead of a looped local file. The architecture does not change; only the frame source changes.

The admin API for this flow is:

- `GET /api/admin/zones` — list all zones with their current `OFFLINE`/`LIVE` feed state.
- `POST /api/admin/zones/{zoneId}/connect-footage` — connect a multipart video file and start its continuous loop.
- `POST /api/admin/zones/{zoneId}/stop-coverage` — stop the zone loop and take the camera offline.

### Video processing jobs

The internal admin workspace is at `http://localhost:3000/admin`. Uploading a video creates a non-blocking processing job: the backend stores the clip in `cv-pipeline/uploads/{jobId}/`, runs `process_video.py`, generates its summary, then internally ingests the generated events through the existing risk-event service. Generated artefacts are isolated in `cv-pipeline/outputs/{jobId}/` and served from `/job-files/{jobId}/`.

The backend assumes that `python` can run the CV dependencies from the `cv-pipeline/` directory. Override these values when necessary:

```text
NIRIKSHAN_PYTHON=C:\\path\\to\\python.exe
NIRIKSHAN_CV_PIPELINE_DIR=C:\\path\\to\\nirikshan\\cv-pipeline
```

Uploads allow up to 1 GB for local prototype footage. The legacy `ProcessingJob` endpoints remain available for developer-only one-off CV runs; the main admin demo flow uses persistent `ZoneFeed` coverage instead.

To build:

```text
mvn clean package
```

Swagger UI is available at `http://localhost:8080/swagger-ui.html`; OpenAPI JSON is at `/v3/api-docs`.

## API

Processing jobs:

- `POST /api/jobs/upload?zoneId={id}` — upload a campus video as multipart field `file`; immediately returns a pending job.
- `GET /api/jobs/{id}` — processing status and result links.
- `GET /api/jobs?zoneId={id}` — job history for a zone; omit `zoneId` for all jobs.

- `POST /api/risk-events` — ingest a CV-generated event; updates its zone and creates an alert for HIGH or CRITICAL.
- `GET /api/venues` — list venues.
- `GET /api/venues/{id}/zones` — list zones for a venue.
- `GET /api/zones/{zoneId}/risk-events?limit=50` — recent events for a zone (limit capped at 200).
- `GET /api/alerts?active=true` — unresolved alerts; omit `active` for all alerts.
- `PATCH /api/alerts/{id}/resolve` — resolve an alert.

STOMP clients connect to `/ws` and subscribe to `/topic/risk-updates` and `/topic/alerts`.

Example requests are in [`requests.http`](requests.http).
