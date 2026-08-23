# Nirikshan architecture and source code guide

Nirikshan is a campus crowd safety prototype. Local computer vision workers process camera footage into privacy safe zone telemetry, while a Spring Boot service stores the telemetry, derives safety signals, serves the web applications, and coordinates operational responses.

![Nirikshan architecture](images/architecture.svg)

## 1. Runtime data flow

### 1.1 Live computer vision flow

1. A local worker is started from `cv-pipeline/run_local_cv_workers.py` or `cv-pipeline/process_video.py`.

2. The worker opens one camera or video source for one backend zone. The shared worker uses `yolo26s.pt` by default and applies overlapping tiled inference. The single video processor accepts a configurable YOLO weight file.

3. `cv-pipeline/privacy.py` processes frames before detection output is written or published. Person detections provide a fallback upper body blur region when face detection alone is insufficient.

4. `cv-pipeline/process_video.py` and `cv-pipeline/shared_worker.py` calculate people count, approximate movement, hotspots, flow signals, perspective weighted density, and a transparent risk score.

5. The worker posts an event to `POST /api/risk-events`. The event contains a zone ID, timestamp, density, headcount, movement speed, risk level, explanation, source, and derived signal fields.

6. The Spring Boot risk event services persist the event, update the zone state, create or resolve alerts, derive recommendations, update forecasts, and publish STOMP notifications.

7. The Next.js dashboard, security workspace, and citizen alerts page read REST data and subscribe to the backend WebSocket endpoint `/ws` for updates.

### 1.2 Recorded demonstration flow

1. Processed event JSON and privacy safe annotated video are stored under `cv-pipeline/outputs/recorded-sessions` for deployment or under the local CV output directory.

2. `frontend/app/console/video-sessions/actions.ts` reads local event files through a server action and can fall back to backend job file endpoints.

3. The Video Sessions page displays the recorded footage and normalized telemetry as recorded material. It does not represent a live camera feed.

### 1.3 Assistant flow

1. `AssistantController` accepts authenticated chat requests and the streaming chat route.

2. `AssistantService` resolves the signed in user's role, limits the visible zones, gathers current zone data, alerts, recommendations, and recent telemetry, and rejects questions outside campus safety and crowd monitoring.

3. `GroqChatClient` sends the scoped system prompt and context to Groq's OpenAI compatible chat completion endpoint. The streaming route forwards server sent events through the backend to `AssistantChatWidget`.

4. The assistant supports English, Hindi, and Odia. It is a question answering layer over application supplied safety context. It is not the detector, risk engine, gate controller, or source of telemetry.

## 2. Source code modules

### 2.1 Frontend

1. `frontend/app/console` contains the administrator dashboard, forecasts, flow intelligence, route recommendations, response actions, management views, check in management, reports, and recorded Video Sessions page.

2. `frontend/app/security` contains the security operator workspace and assigned zone views.

3. `frontend/app/alerts` contains the citizen alert experience, location aware map, reports, announcements, forecasts, route guidance, and alert subscriptions.

4. `frontend/app/components` contains shared shell, assistant, maps, zone markers, charts, forms, theme, and language components.

5. `frontend/app/lib` contains authentication, REST access, streaming access, offline synchronization, language context, and page language helpers.

6. `frontend/app/config/campusRoute.ts` and `frontend/app/config/routeWaypoints.ts` provide shared Leaflet route display data. They are separate from the removed 3D visualization layer.

7. The frontend uses the Next.js App Router. It has server actions for server side file access and no Next.js API route handlers. Browser requests go to the Spring Boot backend.

### 2.2 Backend

1. `src/main/java/com/nirikshan/controller` exposes authentication, telemetry, alerts, forecasts, routes, reports, check ins, announcements, administration, health, and assistant endpoints.

2. `src/main/java/com/nirikshan/service` contains deterministic risk intelligence, event processing, forecasts, flow analysis, route recommendation, privacy retention, scenario handling, assistant context assembly, and Groq access.

3. `src/main/java/com/nirikshan/repository` contains Spring Data JPA repositories for users, venues, zones, telemetry, alerts, recommendations, reports, jobs, check ins, announcements, and audit records.

4. `src/main/java/com/nirikshan/model` contains the JPA entities and enums persisted by the backend.

5. `src/main/java/com/nirikshan/config` contains datasource handling, CORS, request correlation, exception handling, privacy auditing, asynchronous execution, seeding, and OpenAPI configuration.

6. `src/main/java/com/nirikshan/security` contains JWT authentication, current user resolution, role enforcement, and Spring Security configuration.

7. `src/main/java/com/nirikshan/websocket` configures the STOMP endpoint used for live risk updates, alerts, recommendations, forecasts, announcements, and check ins.

8. `src/main/resources/db/migration` contains Flyway migrations. Production uses PostgreSQL. The `dev` profile uses disposable H2 for local fallback testing.

### 2.3 Computer vision pipeline

1. `cv-pipeline/process_video.py` processes one video source as one physical zone and can write annotated footage, event JSON, detection CSV output, and direct backend events.

2. `cv-pipeline/shared_worker.py` supports multiple local zone streams with tiled YOLO inference and round robin processing.

3. `cv-pipeline/run_local_cv_workers.py` supervises independent local worker processes, one process per configured zone.

4. `cv-pipeline/privacy.py` performs frame privacy processing before detection output is exposed.

5. `cv-pipeline/signal_utils.py` derives movement, flow, hotspot, and behavior signals.

6. `cv-pipeline/risk_scoring.py` applies configurable density, density increase, speed drop, and risk score thresholds from `cv-pipeline/thresholds_config.json`.

7. `cv-pipeline/replay_events.py` and `cv-pipeline/replay_scenarios.py` support recorded and deterministic demonstration data. They do not represent live camera inference.

## 3. Third party APIs and services

1. Groq provides the chat completion API used by `GroqChatClient`. The backend uses the OpenAI compatible base URL `https://api.groq.com/openai/v1`. The API key is read from the backend environment variable `GROQ_API_KEY`.

2. OpenStreetMap tile servers provide map tiles through Leaflet and React Leaflet. The application does not require a Google Maps key for its current map views.

3. STOMP.js and SockJS provide the browser client side connection to the Spring Boot WebSocket endpoint. The broker and WebSocket endpoint are part of the backend application.

4. Ultralytics YOLO provides person detection in the local Python pipeline. The default shared worker weight is `yolo26s.pt`. Model weights are loaded locally and are not installed in the Render backend image.

5. PyTorch provides the local inference runtime. CUDA is used when available and the pipeline can fall back to CPU inference.

6. OpenCV provides video capture, frame processing, annotation, and privacy related image operations. FFmpeg is used by the video processor when available to transcode annotated output to browser compatible H.264.

7. Render runs the Docker based Spring Boot backend as described by `render.yaml`. The deployed backend receives events and serves application data. Local GPU workers remain outside the Render container.

## 4. Deployment boundary

The Dockerfile copies the compiled Spring Boot JAR and recorded session assets into a Java 17 runtime image. It does not install Python, PyTorch, OpenCV, FFmpeg, YOLO weights, or a CV worker.

The Render service runs the backend with the production profile. PostgreSQL is configured through `DATABASE_URL`. The frontend can run locally with Next.js or be deployed separately with its API base URL pointed at the backend. The repository's Render blueprint defines the backend service, not a separate frontend service.

## 5. Verification points

1. Backend compilation command: `mvn -q -DskipTests compile`.

2. Frontend production build command: `npm run build` from `frontend`.

3. Focused assistant service tests: `mvn -q -Dtest=AssistantServiceTest test`.

4. CV unit tests include `python -m unittest` style test modules such as `test_signal_utils.py` and `test_privacy.py`.

5. Health verification endpoint: `GET /api/health`.

6. OpenAPI documentation endpoint: `/swagger-ui.html` and `/v3/api-docs`.
