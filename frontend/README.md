# Nirikshan Dashboard

Next.js App Router + TypeScript command dashboard for Nirikshan. It connects directly to the Spring Boot backend and uses no mock data.

The primary hero visualization uses Leaflet with OpenStreetMap tiles and real zone latitude/longitude values returned by the backend. The original SVG bubble-map implementation remains in `app/page.tsx` as an alternate view for future use.

## Role-aware routes

- `/alerts` is the mobile-first citizen/security sign-in experience. Public sign-up creates Citizens only.
- Security accounts are created in the administrator console, must replace their temporary password, then see only their assigned-zone alerts and instructions.
- `/console/login` and the unlinked `/console` route are for administrators.

## Run locally

Start the Spring Boot backend first from the repository root:

```bash
mvn spring-boot:run
```

Then, in this directory:

```bash
npm install
copy .env.local.example .env.local
npm run dev
```

Open [http://localhost:3000](http://localhost:3000).

The public citizen experience is available at [http://localhost:3000/alerts](http://localhost:3000/alerts). It is mobile-first, requests optional location access, sorts active alerts by proximity when permission is granted, and can be installed to a phone home screen as **Nirikshan Alerts**. The PWA manifest, placeholder icon, service worker, and offline fallback are generated from `public/` and `next-pwa` during production builds.

PowerShell users can copy the environment file with:

```powershell
Copy-Item .env.local.example .env.local
```

The default environment values are:

```text
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
NEXT_PUBLIC_WS_URL=ws://localhost:8080/ws
NEXT_PUBLIC_GOOGLE_MAPS_API_KEY=
```

No map API key or billing setup is required. OpenStreetMap tiles are used for the local dashboard map.

## Real backend data flow

On initial load, the dashboard requests:

- `GET /api/venues`
- `GET /api/venues/{id}/zones`
- `GET /api/alerts?active=true`
- `GET /api/health`

Selecting a zone requests `GET /api/zones/{id}/risk-events?limit=50` for its density trend. The dashboard subscribes to STOMP topics `/topic/risk-updates` and `/topic/alerts` through the backend WebSocket endpoint `/ws`. Alert resolution calls `PATCH /api/alerts/{id}/resolve`.

The citizen route uses `GET /api/venues`, `GET /api/venues/{id}/zones`, and `GET /api/alerts?active=true`, subscribes to `/topic/alerts`, and submits community reports through `POST /api/citizen-reports`. Reports can be listed with `GET /api/citizen-reports?zoneId={id}`.

If the backend is unavailable, the page shows a retry state. If no venue or zones are seeded, it shows an empty state rather than fabricated values.

## Demo flow

1. Start the backend.
2. Replay CV events into it:

   ```powershell
   cd ..\cv-pipeline
   python replay_events.py --events test1_single_events.json --speed 2
   ```

3. Keep the dashboard open at `http://localhost:3000`; zone cards, alerts, counters, and the selected-zone trend update from the real backend stream.
