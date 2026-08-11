# Nirikshan Backend

Spring Boot 3.3 / Java 17 backend for the Nirikshan crowd-risk prototype. The Python CV pipeline supplies pre-computed density, movement, risk level, and explanation values; this service stores, broadcasts, and surfaces them.

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

The default `dev` profile uses an in-memory H2 database and seeds KIIT Campus 25 with five campus zones. H2 console: `http://localhost:8080/h2-console` with JDBC URL `jdbc:h2:mem:nirikshan`.

### Video processing jobs

The internal admin workspace is at `http://localhost:3000/admin`. Uploading a video creates a non-blocking processing job: the backend stores the clip in `cv-pipeline/uploads/{jobId}/`, runs `process_video.py`, generates its summary, then internally ingests the generated events through the existing risk-event service. Generated artefacts are isolated in `cv-pipeline/outputs/{jobId}/` and served from `/job-files/{jobId}/`.

The backend assumes that `python` can run the CV dependencies from the `cv-pipeline/` directory. Override these values when necessary:

```text
NIRIKSHAN_PYTHON=C:\\path\\to\\python.exe
NIRIKSHAN_CV_PIPELINE_DIR=C:\\path\\to\\nirikshan\\cv-pipeline
```

Uploads allow up to 1 GB for local prototype footage. Jobs use the same status flow (`PENDING`, `PROCESSING`, `COMPLETE`, `FAILED`) that a future live-stream buffer source can reuse.

To build:

```text
mvn clean package
```

For PostgreSQL, set `SPRING_PROFILES_ACTIVE=prod` and configure `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` (or the corresponding Spring environment variables).

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
