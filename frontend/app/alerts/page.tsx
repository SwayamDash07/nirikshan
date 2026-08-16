"use client";

import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import dynamic from "next/dynamic";
import { Client, IMessage } from "@stomp/stompjs";
import AppShell, { NavItem } from "../components/AppShell";
import LiveDensityChart from "../components/LiveDensityChart";
import { Button, Card, Field, Input, Spinner } from "../components/ui";
import CampusLocationPicker, {
  type CampusPoint,
  type CampusVenue,
  type VenueSelectionSource,
  distanceBetween,
  venueIsCovered,
} from "../components/CampusLocationPicker";
import {
  api,
  clearSession,
  readSession,
  saveSession,
  type Session,
} from "../lib/auth";
import { useAiLanguage } from "../lib/aiLanguageContext";
import styles from "./citizen.module.css";

const CitizenMiniMap = dynamic(() => import("./CitizenMiniMap"), {
  ssr: false,
  loading: () => <div className={styles.mapLoading}>Loading safety map</div>,
});
type RiskLevel = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
type Zone = {
  id: number;
  name: string;
  latitude: number;
  longitude: number;
  currentDensity: number;
  currentRiskLevel: RiskLevel;
  lastUpdated: string;
};
type Venue = CampusVenue;
type Alert = {
  id: number;
  zoneId: number;
  zoneName?: string;
  timestamp: string;
  message: string;
  severity: RiskLevel;
  resolved: boolean;
  source?: "LIVE" | "SIMULATION";
};
type Recommendation = { id: number; zoneId?: number | null; zoneName?: string | null; type: "OPEN_ROUTE"; message: string; severity: RiskLevel; createdAt: string; status: "PENDING" | "ACKNOWLEDGED" | "DISMISSED"; source?: "LIVE" | "SIMULATION" };
type RiskEvent = { zoneId: number; timestamp: string; densityScore: number; peopleCount: number; riskLevel: RiskLevel; source?: "LIVE" | "SIMULATION" };
type CitizenForecast = { zoneId: number; zoneName: string; generatedAt: string; lastTelemetryAt?: string; currentRisk: RiskLevel; projectedRisk: RiskLevel; state: "STABLE" | "RISING" | "SURGE_RISK" | "CRUSH_RISK" | "RECOVERING" | "INSUFFICIENT_DATA"; message: string; stale: boolean; source?: "LIVE" | "SIMULATION" };
type IncidentSummary = { summary: string; language: string; scope: string; generatedAt: string };
const WS_URL = process.env.NEXT_PUBLIC_WS_URL || "ws://localhost:8080/ws";
type Point = { lat: number; lng: number };
const labels: Record<RiskLevel, string> = {
  LOW: "Normal",
  MEDIUM: "Watch",
  HIGH: "High",
  CRITICAL: "Critical",
};
const navItems: NavItem[] = [
  { label: "Safety alerts", href: "/alerts", icon: "bell" },
  { label: "Report issue", href: "/alerts/report", icon: "activity" },
  { label: "Account", href: "/alerts/security", icon: "settings" },
];

function ago(value: string) {
  const seconds = Math.max(
    0,
    Math.round((Date.now() - new Date(value).valueOf()) / 1000),
  );
  return seconds < 60
    ? "Just now"
    : seconds < 3600
      ? `${Math.floor(seconds / 60)} min ago`
      : `${Math.floor(seconds / 3600)} hr ago`;
}
function point(zones: Zone[], id: number): Point {
  const zone = zones.find((item) => item.id === id);
  return zone
    ? { lat: zone.latitude, lng: zone.longitude }
    : { lat: 20.3641, lng: 85.8163 };
}

function Auth({ done }: { done: (session: Session) => void }) {
  const [signup, setSignup] = useState(false);
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [working, setWorking] = useState(false);
  async function submit(event: FormEvent) {
    event.preventDefault();
    setWorking(true);
    setError("");
    try {
      const session = await api<Session>(
        `/api/auth/${signup ? "signup" : "login"}`,
        {
          method: "POST",
          body: JSON.stringify(
            signup ? { name, email, password } : { email, password },
          ),
        },
      );
      saveSession(session);
      done(session);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Could not sign in");
    } finally {
      setWorking(false);
    }
  }
  return (
    <main className={styles.authPage}>
      <section className={styles.authStory}>
        <span className={styles.authBrand}>Nirikshan</span>
        <div>
          <span className={styles.eyebrow}>CAMPUS SAFETY NETWORK</span>
          <h1>Know what is happening around you.</h1>
          <p>
            Clear updates from the safety team, organized around your location
            and the places you use every day.
          </p>
        </div>
        <div className={styles.storyPoints}>
          <span>
            <b>01</b>Active conditions in one view
          </span>
          <span>
            <b>02</b>Short, clear safety instructions
          </span>
          <span>
            <b>03</b>Direct reporting to the safety team
          </span>
        </div>
      </section>
      <section className={styles.authPanel}>
        <div className={styles.authPanelTop}>
          <span>Citizen access</span>
          <span className={styles.authStatus}>Secure connection</span>
        </div>
        <form className={styles.authCard} onSubmit={submit}>
          <div className={styles.eyebrow}>NIRIKSHAN ALERTS</div>
          <h2>{signup ? "Create your account" : "Welcome back"}</h2>
          <p className={styles.authIntro}>
            {signup
              ? "Create a citizen account to receive campus updates."
              : "Sign in to see current campus safety information."}
          </p>
          {signup && (
            <Field label="Full name">
              <Input
                value={name}
                onChange={(event) => setName(event.target.value)}
                autoComplete="name"
                required
              />
            </Field>
          )}
          <Field label="Email">
            <Input
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              type="email"
              autoComplete="email"
              required
            />
          </Field>
          <Field label="Password" hint="Use at least 8 characters">
            <Input
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              type="password"
              autoComplete={signup ? "new-password" : "current-password"}
              minLength={8}
              required
            />
          </Field>
          {error && (
            <div className={styles.error} role="alert">
              {error}
            </div>
          )}
          <Button size="lg" className={styles.fullButton} disabled={working}>
            {working
              ? "Please wait"
              : signup
                ? "Create citizen account"
                : "Sign in"}
          </Button>
          <button
            className={styles.textButton}
            type="button"
            onClick={() => {
              setSignup(!signup);
              setError("");
            }}
          >
            {signup
              ? "Already registered? Sign in"
              : "New here? Create a citizen account"}
          </button>
          <small className={styles.formNote}>
            Security and administrator accounts are issued by the safety team.
          </small>
        </form>
      </section>
    </main>
  );
}

function CitizenSummary({ enabled }: { enabled: boolean }) {
  const { language } = useAiLanguage();
  const [summary, setSummary] = useState<IncidentSummary>();
  const [summaryLoading, setSummaryLoading] = useState(false);
  const [summaryError, setSummaryError] = useState("");

  async function generateSummary() {
    setSummaryLoading(true);
    setSummaryError("");
    try {
      const next = await api<IncidentSummary>(`/api/venue/incident-summary?language=${language}`);
      setSummary(next);
    } catch (reason) {
      setSummaryError(reason instanceof Error ? reason.message : "Could not generate a campus summary");
    } finally {
      setSummaryLoading(false);
    }
  }

  return <section className={styles.aiSummaryCard} aria-live="polite">
    <div>
      <span className={styles.kicker}>AI CAMPUS SUMMARY</span>
      <h2>Current safety overview</h2>
      <p>Generate a concise, live overview in the selected language.</p>
    </div>
    <button className={styles.refreshButton} type="button" onClick={generateSummary} disabled={summaryLoading || !enabled}>
      {summaryLoading ? "Generating" : "Generate summary"}
    </button>
    {summary && <p className={styles.aiSummaryText}>{summary.summary}</p>}
    {summaryError && <p className={styles.aiSummaryError} role="alert">{summaryError}</p>}
  </section>;
}

function Citizen({ session }: { session: Session }) {
  const [venues, setVenues] = useState<Venue[]>([]);
  const [venue, setVenue] = useState<Venue>();
  const [zones, setZones] = useState<Zone[]>([]);
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [recommendations, setRecommendations] = useState<Recommendation[]>([]);
  const [forecasts, setForecasts] = useState<CitizenForecast[]>([]);
  const [simulationActive, setSimulationActive] = useState(false);
  const [densityHistory, setDensityHistory] = useState<number[]>([]);
  const [location, setLocation] = useState<Point>();
  const [selectionSource, setSelectionSource] =
    useState<VenueSelectionSource>("default");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const availableVenues = venues.length
        ? venues
        : await api<Venue[]>("/api/venues");
      if (!availableVenues.length)
        throw new Error("No venue is available yet.");
      if (!venues.length) setVenues(availableVenues);
      const savedId = Number(
        window.localStorage.getItem("nirikshan.selectedVenue"),
      );
      const selected =
        availableVenues.find((item) => item.id === savedId) ||
        venue ||
        availableVenues[0];
      const [venueZones, activeAlerts] = await Promise.all([
        api<Zone[]>(`/api/venues/${selected.id}/zones`),
        api<Alert[]>("/api/alerts?active=true"),
      ]);
      const [routeResult, eventsResult, forecastResult] = await Promise.allSettled([
        api<Recommendation[]>("/api/recommendations/customer?active=true"),
        api<RiskEvent[]>(`/api/venues/${selected.id}/risk-events?limit=120`),
        api<CitizenForecast[]>(`/api/venue/risk-forecast?venueId=${selected.id}`),
      ]);
      const routeRecommendations = routeResult.status === "fulfilled" ? routeResult.value : [];
      const recentEvents = eventsResult.status === "fulfilled" ? eventsResult.value : [];
      setForecasts(forecastResult.status === "fulfilled" ? forecastResult.value : []);
      setVenue(selected);
      setZones(venueZones);
      const zoneIds = new Set(venueZones.map((zone) => zone.id));
      setAlerts(activeAlerts.filter((alert) => zoneIds.has(alert.zoneId)));
      setRecommendations(routeRecommendations);
      setSimulationActive(recentEvents[0]?.source === "SIMULATION");
      const history = recentEvents.slice().reverse().map((event) => event.densityScore).filter(Number.isFinite).slice(-30);
      const currentAverage = venueZones.length ? venueZones.reduce((sum, zone) => sum + zone.currentDensity, 0) / venueZones.length : 0;
      setDensityHistory(history.length ? history : currentAverage > 0 ? [currentAverage] : []);
    } catch (reason) {
      setError(
        reason instanceof Error
          ? reason.message
          : "Could not load safety alerts",
      );
    } finally {
      setLoading(false);
    }
  }, [venue, venues]);
  useEffect(() => {
    if (!venue) return;
    const timer = window.setInterval(() => {
      api<CitizenForecast[]>(`/api/venue/risk-forecast?venueId=${venue.id}`).then(setForecasts).catch(() => undefined);
    }, 15000);
    return () => window.clearInterval(timer);
  }, [venue?.id]);
  useEffect(() => {
    load();
  }, []);
  const selectVenue = useCallback(
    (next: CampusVenue, source: VenueSelectionSource) => {
      setSelectionSource(source);
      setVenue(next);
      window.localStorage.setItem("nirikshan.selectedVenue", String(next.id));
    },
    [],
  );
  useEffect(() => {
    if (!venue || !venues.length) return;
    load();
  }, [venue?.id]);
  useEffect(() => {
    const client = new Client({ brokerURL: WS_URL, reconnectDelay: 5000, onConnect: () => {
      client.subscribe("/topic/risk-updates", (message: IMessage) => {
        const event = JSON.parse(message.body) as RiskEvent;
        setSimulationActive(event.source === "SIMULATION");
        setZones((current) => {
          const next = current.map((zone) => zone.id === event.zoneId ? { ...zone, currentDensity: event.densityScore, currentPeopleCount: event.peopleCount, currentRiskLevel: event.riskLevel, lastUpdated: event.timestamp } : zone);
          const average = next.length ? next.reduce((sum, zone) => sum + zone.currentDensity, 0) / next.length : event.densityScore;
          setDensityHistory((history) => [...history, average].slice(-30));
          return next;
        });
      });
      client.subscribe("/topic/alerts", (message: IMessage) => {
        const alert = JSON.parse(message.body) as Alert;
        setAlerts((current) => alert.resolved ? current.filter((item) => item.id !== alert.id) : [alert, ...current.filter((item) => item.id !== alert.id)]);
      });
      client.subscribe("/topic/recommendations", (message: IMessage) => {
        const recommendation = JSON.parse(message.body) as Recommendation;
        if (recommendation.type !== "OPEN_ROUTE") return;
        setRecommendations((current) => recommendation.status === "PENDING" ? [recommendation, ...current.filter((item) => item.id !== recommendation.id)] : current.filter((item) => item.id !== recommendation.id));
      });
    }});
    client.activate();
    return () => { client.deactivate(); };
  }, []);
  const onLocationChange = useCallback(
    (next: CampusPoint) => setLocation(next),
    [],
  );
  const sorted = useMemo(
    () =>
      [...alerts].sort((a, b) =>
        location
          ? distanceBetween(location, point(zones, a.zoneId)) -
            distanceBetween(location, point(zones, b.zoneId))
          : +new Date(b.timestamp) - +new Date(a.timestamp),
      ),
    [alerts, location, zones],
  );
  const coverageUnavailable =
    selectionSource !== "search" &&
    selectionSource !== "nearest" &&
    location &&
    venue &&
    !venueIsCovered(venue, location);
  if (session.user.mustChangePassword) {
    window.location.replace("/alerts/security");
    return <main className={styles.centerState}>Opening account security</main>;
  }
  return (
    <AppShell
      user={session.user}
      title="Safety alerts"
      subtitle={`${venue?.name || "Your campus"} and current safety information`}
      active="Safety alerts"
      navItems={navItems}
      assistantZones={zones.map((zone) => ({ id: zone.id, name: zone.name }))}
    >
      <div className={styles.citizenIntro}>
        <div>
          <span className={styles.eyebrow}>YOUR SAFETY BRIEFING</span>
          <h2>Good to see you, {session.user.name.split(" ")[0]}.</h2>
          <p>Here is the latest picture from your campus safety network.</p>
        </div>
        <button
          className={styles.refreshButton}
          type="button"
          onClick={load}
          disabled={loading}
        >
          {loading ? "Refreshing" : "Refresh data"}
        </button>
      </div>
      <CitizenSummary enabled={Boolean(venue)} />
      <CampusLocationPicker
        venues={venues}
        selectedVenue={venue}
        location={location}
        onLocationChange={onLocationChange}
        onSelect={selectVenue}
      />
      {error && (
        <div className={styles.errorBanner} role="alert">
          {error}
          <button type="button" onClick={load}>
            Try again
          </button>
        </div>
      )}
      {loading ? (
        <Spinner label="Loading campus safety data" />
      ) : coverageUnavailable ? (
        <div className={styles.serviceUnavailable}>
          <span className={styles.eyebrow}>SERVICE AREA</span>
          <h2>We don’t provide services here yet.</h2>
          <p>
            Live location is outside the supported campus area. Search for
            Campus-25, KIIT, or another supported campus above to view its
            safety services.
          </p>
        </div>
      ) : (
        <>
          <section className={styles.citizenGrid}>
            <Card className={styles.mapCard}>
              <div className={styles.cardHeader}>
                <div>
                  <span className={styles.kicker}>LOCATION CONTEXT</span>
                  <h2>Campus map</h2>
                  <p>Nearby areas and current conditions.</p>
                </div>
                <span className={styles.locationState}>
                  <i />
                  {location ? "Location on" : "Location off"}
                </span>
              </div>
              <CitizenMiniMap
                zones={zones}
                alerts={alerts}
                location={location}
              />
            </Card>
            <Card className={styles.briefCard}>
              <span className={styles.kicker}>LIVE BRIEFING</span>
              <h2>
                {sorted.length
                  ? `${sorted.length} active updates`
                  : "All clear for now"}
              </h2>
              <p>
                {sorted.length
                  ? "Review the highest priority updates first."
                  : "No active conditions have been published for your campus."}
              </p>
              <div className={styles.briefStat}>
                <span>Nearest update</span>
                <strong>
                  {sorted[0]
                    ? sorted[0].zoneName || `Zone ${sorted[0].zoneId}`
                    : "No active update"}
                </strong>
              </div>
              <a href="#alerts" className={styles.primaryLink}>
                Review safety alerts <span>→</span>
              </a>
            </Card>
          </section>
          {simulationActive || alerts.some((alert) => alert.source === "SIMULATION") || recommendations.some((recommendation) => recommendation.source === "SIMULATION") || forecasts.some((forecast) => forecast.source === "SIMULATION") ? <div className={styles.simulationBanner} role="status">SIMULATION MODE: Deterministic drill data is being shown. It is not live camera telemetry.</div> : null}
          {forecasts.filter((forecast) => forecast.state !== "STABLE" || forecast.stale).map((forecast) => <Card className={styles.forecastCard} key={forecast.zoneId}><div className={styles.cardHeader}><div><span className={styles.kicker}>EARLY SAFETY UPDATE</span><h2>{forecast.zoneName}</h2><p>{forecast.message}</p></div>{forecast.source === "SIMULATION" && <span className={styles.simulationBadge}>SIMULATION</span>}</div><small className={styles.forecastAge}>{forecast.stale ? "Stale data" : `Updated ${ago(forecast.lastTelemetryAt || forecast.generatedAt)}`}</small></Card>)}
          <section className={styles.customerLiveGrid}>
            <Card className={styles.trendCard}><div className={styles.cardHeader}><div><span className={styles.kicker}>LIVE SIGNAL</span><h2>Campus density trend</h2><p>Recent readings with live updates from the venue risk loop.</p></div></div><LiveDensityChart values={densityHistory} /></Card>
            <Card className={styles.routeCard}><div className={styles.cardHeader}><div><span className={styles.kicker}>ROUTE RECOMMENDATIONS</span><h2>Safer routes</h2><p>Actionable guidance for moving through campus.</p></div><span className={styles.routeCount}>{recommendations.length}</span></div>{recommendations.length ? <div className={styles.routeList}>{recommendations.map((recommendation) => <article key={recommendation.id}><span className={`${styles.severity} ${styles[`severity${recommendation.severity}`]}`}>{labels[recommendation.severity]}</span>{recommendation.source === "SIMULATION" && <span className={styles.simulationBadge}>SIMULATION</span>}<strong>{recommendation.message}</strong><small>{ago(recommendation.createdAt)}</small></article>)}</div> : <div className={styles.routeEmpty}>No route changes are recommended right now.</div>}</Card>
          </section>
          <section id="alerts" className={styles.alertSection}>
            <div className={styles.sectionHeading}>
              <div>
                <span className={styles.kicker}>SAFETY ALERTS</span>
                <h2>Updates near you</h2>
              </div>
              <span>{sorted.length} active</span>
            </div>
            {sorted.length ? (
              <div className={styles.alertGrid}>
                {sorted.map((alert) => (
                  <article className={styles.alertCard} key={alert.id}>
                    <div className={styles.alertTop}>
                      <span
                        className={`${styles.severity} ${styles[`severity${alert.severity}`]}`}
                      >
                        {labels[alert.severity]}
                      </span>
                      {alert.source === "SIMULATION" && <span className={styles.simulationBadge}>SIMULATION</span>}
                      {location && (
                        <span className={styles.distance}>
                          {distanceBetween(
                            location,
                            point(zones, alert.zoneId),
                          )}
                          m away
                        </span>
                      )}
                    </div>
                    <h3>{alert.zoneName || `Zone ${alert.zoneId}`}</h3>
                    <p>{alert.message}</p>
                    <small>{ago(alert.timestamp)}</small>
                  </article>
                ))}
              </div>
            ) : (
              <div className={styles.emptyState}>
                No active alerts. The safety team has not published any current
                updates.
              </div>
            )}
          </section>
        </>
      )}
    </AppShell>
  );
}

export default function Page() {
  const [session, setSession] = useState<Session | null>(null);
  useEffect(() => {
    const next = readSession();
    if (!next) return;
    const customerPreview =
      next.user.role === "ADMIN" &&
      new URLSearchParams(window.location.search).get("preview") === "customer";
    if (next.user.role === "ADMIN" && !customerPreview) {
      window.location.replace("/console");
      return;
    }
    if (next.user.role === "SECURITY") {
      window.location.replace("/security");
      return;
    }
    setSession(next);
  }, []);
  if (!session) return <Auth done={setSession} />;
  return <Citizen session={session} />;
}
