"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import dynamic from "next/dynamic";
import { Client, IMessage } from "@stomp/stompjs";
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import styles from "./console.module.css";
import { api, clearSession, readSession } from "../lib/auth";
import AdminManagement from "./AdminManagement";

const LeafletVenueMap = dynamic(() => import("../LeafletVenueMap"), {
  ssr: false,
  loading: () => <div className={styles.emptyPanel}>Loading OpenStreetMap layer…</div>,
});

type RiskLevel = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

type Zone = {
  id: number;
  name: string;
  latitude: number;
  longitude: number;
  radiusMeters?: number;
  currentDensity: number;
  currentPeopleCount: number;
  currentRiskLevel: RiskLevel;
  lastUpdated: string;
};

type Venue = {
  id: number;
  name: string;
  description?: string;
};

type RiskEvent = {
  id?: number;
  zoneId: number;
  timestamp: string;
  densityScore: number;
  peopleCount?: number;
  movementSpeed: number;
  riskLevel: RiskLevel;
  explanation: string;
  sourceClipId?: string;
};

type Alert = {
  id: number;
  zoneId: number;
  zoneName?: string;
  timestamp: string;
  message: string;
  severity: RiskLevel;
  resolved: boolean;
  resolvedAt?: string;
};

type Health = {
  status: string;
  totalZones: number;
  totalRiskEvents: number;
  activeAlerts: number;
};

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";
const WS_URL = process.env.NEXT_PUBLIC_WS_URL || "ws://localhost:8080/ws";

const riskMeta: Record<RiskLevel, { label: string; color: string; soft: string }> = {
  LOW: { label: "Normal", color: "#24a26a", soft: "#e6f7ef" },
  MEDIUM: { label: "Watch", color: "#d69a16", soft: "#fff6dd" },
  HIGH: { label: "High", color: "#e97825", soft: "#fff0e5" },
  CRITICAL: { label: "Critical", color: "#d84b54", soft: "#ffe9eb" },
};

const riskRank: Record<RiskLevel, number> = { LOW: 0, MEDIUM: 1, HIGH: 2, CRITICAL: 3 };

async function fetchJson<T>(path: string): Promise<T> { return api<T>(path); }

function formatTime(value?: string) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.valueOf()) ? "—" : date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

function formatAge(value?: string) {
  if (!value) return "Awaiting signal";
  const seconds = Math.max(0, Math.round((Date.now() - new Date(value).valueOf()) / 1000));
  if (seconds < 60) return `${seconds}s ago`;
  return `${Math.floor(seconds / 60)}m ago`;
}

function highestRisk(zones: Zone[]): RiskLevel {
  return zones.reduce<RiskLevel>((highest, zone) => (
    riskRank[zone.currentRiskLevel] > riskRank[highest] ? zone.currentRiskLevel : highest
  ), "LOW");
}

function zonePosition(zone: Zone, zones: Zone[]) {
  const lats = zones.map((item) => item.latitude);
  const lngs = zones.map((item) => item.longitude);
  const latSpan = Math.max(Math.max(...lats) - Math.min(...lats), 0.0008);
  const lngSpan = Math.max(Math.max(...lngs) - Math.min(...lngs), 0.0008);
  const x = 12 + ((zone.longitude - Math.min(...lngs)) / lngSpan) * 76;
  const y = 84 - ((zone.latitude - Math.min(...lats)) / latSpan) * 68;
  return { left: `${x}%`, top: `${y}%` };
}

function Icon({ name }: { name: "grid" | "pin" | "bell" | "shield" | "arrow" | "activity" }) {
  const paths = {
    grid: <><rect x="3" y="3" width="7" height="7" rx="1" /><rect x="14" y="3" width="7" height="7" rx="1" /><rect x="3" y="14" width="7" height="7" rx="1" /><rect x="14" y="14" width="7" height="7" rx="1" /></>,
    pin: <><path d="M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0Z" /><circle cx="12" cy="10" r="2.5" /></>,
    bell: <><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9Z" /><path d="M10 21h4" /></>,
    shield: <><path d="M12 3 20 6v5c0 5.2-3.3 8.7-8 10-4.7-1.3-8-4.8-8-10V6l8-3Z" /><path d="m8.5 12 2.2 2.2 4.8-5" /></>,
    arrow: <><path d="M5 12h14" /><path d="m13 6 6 6-6 6" /></>,
    activity: <><path d="M3 12h4l2-7 4 14 2-7h6" /></>,
  }[name];
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths}</svg>;
}

function StatusBadge({ level }: { level: RiskLevel }) {
  const meta = riskMeta[level];
  return <span className={styles.statusBadge} style={{ color: meta.color, background: meta.soft }}><i style={{ background: meta.color }} />{meta.label}</span>;
}

function MapPanel({ zones, selectedId, onSelect }: { zones: Zone[]; selectedId?: number; onSelect: (id: number) => void }) {
  if (!zones.length) return <div className={styles.emptyPanel}>No mapped zones available yet.</div>;
  const maxDensity = Math.max(...zones.map((zone) => zone.currentDensity), 1);
  return (
    <div className={styles.mapPanel}>
      <div className={styles.mapTopline}><span className={styles.liveDot} /> Live venue telemetry <span className={styles.mapCoordinates}>KIIT Campus 25 · Bhubaneswar</span></div>
      <div className={styles.mapCanvas}>
        <div className={styles.mapGrid} />
        <div className={`${styles.mapRoad} ${styles.roadOne}`} /><div className={`${styles.mapRoad} ${styles.roadTwo}`} /><div className={`${styles.mapRoad} ${styles.roadThree}`} />
        <div className={styles.mapRiver} />
        {zones.map((zone) => {
          const position = zonePosition(zone, zones);
          const meta = riskMeta[zone.currentRiskLevel];
          const size = 30 + Math.min(34, (zone.currentDensity / maxDensity) * 34);
          return (
            <button key={zone.id} className={`${styles.zoneMarker} ${zone.currentRiskLevel === "CRITICAL" ? styles.criticalPulse : ""}`} style={{ ...position, width: size, height: size, borderColor: meta.color, color: meta.color }} onClick={() => onSelect(zone.id)} aria-label={`Select ${zone.name}`}>
              <span style={{ background: meta.color }} />
              <b>{zone.id}</b>
              {selectedId === zone.id && <em />}
            </button>
          );
        })}
        <div className={styles.mapLegend}><span><i style={{ background: riskMeta.LOW.color }} />Normal</span><span><i style={{ background: riskMeta.MEDIUM.color }} />Watch</span><span><i style={{ background: riskMeta.CRITICAL.color }} />Critical</span></div>
        <div className={styles.mapScale}><span>LIVE MAP LAYER</span><strong>{zones.length} zones</strong></div>
      </div>
    </div>
  );
}

function ZoneCard({ zone, selected, onClick }: { zone: Zone; selected: boolean; onClick: () => void }) {
  const meta = riskMeta[zone.currentRiskLevel];
  return (
    <button className={`${styles.zoneCard} ${selected ? styles.zoneCardSelected : ""}`} onClick={onClick}>
      <div className={styles.cardHeading}><div><span className={styles.zoneKicker}>ZONE {String(zone.id).padStart(2, "0")}</span><h3>{zone.name}</h3></div><StatusBadge level={zone.currentRiskLevel} /></div>
      <div className={styles.zoneMetrics}><div><span>Headcount</span><strong>{zone.currentPeopleCount ?? 0} <small>people</small></strong></div><div><span>Density</span><strong>{zone.currentDensity.toFixed(2)} <small>p/m²</small></strong></div><div><span>Updated</span><strong>{formatTime(zone.lastUpdated)}</strong></div></div>
      <div className={styles.cardFooter}><span className={styles.signalLine} style={{ background: meta.color }} /><span>{formatAge(zone.lastUpdated)}</span><Icon name="arrow" /></div>
    </button>
  );
}

function TrendPanel({ zone, events }: { zone?: Zone; events: RiskEvent[] }) {
  const first = events[0]?.timestamp;
  const chartData = [...events].reverse().map((event) => ({
    time: first ? Math.max(0, Math.round((new Date(event.timestamp).valueOf() - new Date(first).valueOf()) / 1000)) : 0,
    density: event.densityScore,
  }));
  return (
    <div className={styles.navyPanel}>
      <div className={styles.panelHeader}><div><span className={styles.eyebrow}>LIVE ANALYTICS</span><h2>Zone Density Trend</h2><p>{zone ? zone.name : "Select a zone to inspect its signal"}</p></div><span className={styles.panelIcon}><Icon name="activity" /></span></div>
      {chartData.length > 1 ? <div className={styles.chartWrap}><ResponsiveContainer width="100%" height="100%"><LineChart data={chartData}><defs><linearGradient id="densityLine" x1="0" y1="0" x2="1" y2="0"><stop offset="0%" stopColor="#6fe6db" /><stop offset="100%" stopColor="#5ca8ff" /></linearGradient></defs><CartesianGrid stroke="#ffffff18" vertical={false} /><XAxis dataKey="time" tick={{ fill: "#93a6c3", fontSize: 11 }} tickLine={false} axisLine={false} tickFormatter={(value) => `${value}s`} /><YAxis tick={{ fill: "#93a6c3", fontSize: 11 }} tickLine={false} axisLine={false} width={34} /><Tooltip contentStyle={{ background: "#132544", border: "1px solid #2b496e", borderRadius: 10, color: "#fff" }} labelFormatter={(value) => `+${value}s`} formatter={(value) => [`${Number(value).toFixed(2)} p/m²`, "Density"]} /><Line type="monotone" dataKey="density" stroke="url(#densityLine)" strokeWidth={3} dot={{ r: 3, fill: "#7be6da", strokeWidth: 0 }} activeDot={{ r: 5 }} /></LineChart></ResponsiveContainer></div> : <div className={styles.chartEmpty}>Not enough risk events for a trend yet.<br /><span>Replay CV events to populate this signal.</span></div>}
    </div>
  );
}

function App() {
  const [venue, setVenue] = useState<Venue>();
  const [zones, setZones] = useState<Zone[]>([]);
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [health, setHealth] = useState<Health>();
  const [events, setEvents] = useState<RiskEvent[]>([]);
  const [selectedZoneId, setSelectedZoneId] = useState<number>();
  const [loading, setLoading] = useState(true);
  const [eventsLoading, setEventsLoading] = useState(false);
  const [error, setError] = useState("");
  const [connected, setConnected] = useState(false);
  const stompRef = useRef<Client | null>(null);

  const selectedZone = zones.find((zone) => zone.id === selectedZoneId);
  const overallRisk = highestRisk(zones);

  const [authorized, setAuthorized] = useState(false);
  const loadInitial = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const venues = await fetchJson<Venue[]>("/api/venues");
      if (!venues.length) { setVenue(undefined); setZones([]); setLoading(false); return; }
      const currentVenue = venues[0];
      const [venueZones, activeAlerts, status] = await Promise.all([
        fetchJson<Zone[]>(`/api/venues/${currentVenue.id}/zones`),
        fetchJson<Alert[]>("/api/alerts?active=true"),
        fetchJson<Health>("/api/health"),
      ]);
      setVenue(currentVenue);
      setZones(venueZones);
      setAlerts(activeAlerts);
      setHealth(status);
      setSelectedZoneId((current) => current && venueZones.some((zone) => zone.id === current) ? current : venueZones[0]?.id);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not connect to the Nirikshan backend.");
    } finally { setLoading(false); }
  }, []);

  const loadEvents = useCallback(async (zoneId: number) => {
    setEventsLoading(true);
    try { setEvents(await fetchJson<RiskEvent[]>(`/api/zones/${zoneId}/risk-events?limit=50`)); }
    catch { setEvents([]); }
    finally { setEventsLoading(false); }
  }, []);

  useEffect(() => { const session = readSession(); if (!session || session.user.role !== "ADMIN" || session.user.mustChangePassword) { clearSession(); window.location.replace("/console/login"); return; } setAuthorized(true); loadInitial(); }, [loadInitial]);
  useEffect(() => { if (selectedZoneId) loadEvents(selectedZoneId); }, [selectedZoneId, loadEvents]);

  useEffect(() => {
    const client = new Client({
      brokerURL: WS_URL,
      reconnectDelay: 5000,
      onConnect: () => { setConnected(true); client.subscribe("/topic/risk-updates", (message: IMessage) => {
        const event = JSON.parse(message.body) as RiskEvent;
        setZones((current) => current.map((zone) => zone.id === event.zoneId ? { ...zone, currentDensity: event.densityScore, currentPeopleCount: event.peopleCount ?? zone.currentPeopleCount ?? 0, currentRiskLevel: event.riskLevel, lastUpdated: event.timestamp } : zone));
        setHealth((current) => current ? { ...current, totalRiskEvents: current.totalRiskEvents + 1 } : current);
        if (event.zoneId === selectedZoneId) setEvents((current) => [event, ...current].slice(0, 50));
      }); client.subscribe("/topic/alerts", (message: IMessage) => {
        const alert = JSON.parse(message.body) as Alert;
        setAlerts((current) => [alert, ...current.filter((item) => item.id !== alert.id)]);
        setHealth((current) => current ? { ...current, activeAlerts: current.activeAlerts + (alert.resolved ? 0 : 1) } : current);
      }); },
      onDisconnect: () => setConnected(false),
      onWebSocketError: () => setConnected(false),
      onStompError: () => setConnected(false),
    });
    client.activate();
    stompRef.current = client;
    return () => { client.deactivate(); };
  }, [selectedZoneId]);

  const resolveAlert = async (id: number) => {
    try {
      await api<Alert>(`/api/alerts/${id}/resolve`, { method: "PATCH" });
      setAlerts((current) => current.filter((alert) => alert.id !== id));
      setHealth((current) => current ? { ...current, activeAlerts: Math.max(0, current.activeAlerts - 1) } : current);
    } catch (err) { setError(err instanceof Error ? err.message : "Could not resolve alert."); }
  };

  const activeCameras = health?.totalZones ?? zones.length;
  const activeAlerts = alerts.filter((alert) => !alert.resolved).length;
  const totalHeadcount = zones.reduce((sum, zone) => sum + (zone.currentPeopleCount ?? 0), 0);
  if (!authorized) return <main className={styles.loading}>Checking command access…</main>;
  return (
    <main className={styles.shell}>
      <header className={styles.navbar}>
        <a href="#dashboard" className={styles.brand}><span className={styles.brandMark}><Icon name="shield" /></span><span><b>Nirikshan</b><small>AI-powered crowd safety intelligence</small></span></a>
        <nav><a href="#dashboard">Dashboard</a><a href="#zones">Zones</a><a href="#alerts">Alerts <span>{activeAlerts}</span></a><a href="#about">About</a><a href="/admin">Video upload</a><button onClick={() => { clearSession(); window.location.replace("/console/login"); }}>Sign out</button></nav>
        <div className={styles.navStatus}><span className={connected ? styles.liveDot : styles.offlineDot} />{connected ? "LIVE" : "CONNECTING"}<small>Command link</small></div>
      </header>

      {loading ? <div className={styles.loading}><span className={styles.spinner} />Loading live venue telemetry…</div> : error ? <div className={styles.errorBanner}><span>Backend unavailable</span><p>{error}</p><button onClick={loadInitial}>Retry connection</button></div> : (
        <>
          <section id="dashboard" className={styles.heroIntro}><div><span className={styles.eyebrowLight}>COMMAND DASHBOARD / {venue?.name || "NO VENUE"}</span><h1>Live venue monitoring</h1><p>See crowd movement signals early. Coordinate safer decisions with a clear view of every active zone.</p></div><div className={styles.heroMeta}><span className={styles.heroMetaIcon}><Icon name="shield" /></span><div><strong>Predictive safety layer</strong><span>Real CV events · explainable risk</span></div></div></section>
          <section className={styles.mapSection}><LeafletVenueMap venue={venue} zones={zones} selectedId={selectedZoneId} onSelect={setSelectedZoneId} /></section>
          <section id="zones" className={styles.section}><div className={styles.sectionHeader}><div><span className={styles.eyebrow}>ZONE REGISTER</span><h2>Current zone status</h2></div><span className={styles.sectionHint}><span className={styles.liveDot} />Updates stream automatically</span></div>{zones.length ? <div className={styles.zoneGrid}>{zones.map((zone) => <ZoneCard key={zone.id} zone={zone} selected={zone.id === selectedZoneId} onClick={() => setSelectedZoneId(zone.id)} />)}</div> : <div className={styles.emptyPanel}>No zones are seeded in the backend yet.</div>}</section>
          <section className={styles.analyticsGrid}><div className={styles.navyPanel}><div className={styles.panelHeader}><div><span className={styles.eyebrow}>SYSTEM STATUS</span><h2>Infrastructure health</h2><p>Real-time backend and camera coverage</p></div><span className={styles.operational}><i />Operational</span></div><div className={styles.counterGrid}><div><span>Zones monitored</span><strong>{health?.totalZones ?? zones.length}</strong><small>Backend registered</small></div><div><span>Active cameras</span><strong>{activeCameras}</strong><small>One camera / zone model</small></div><div><span>Active alerts</span><strong>{activeAlerts}</strong><small>Requires attention</small></div><div><span>People tracked</span><strong>{totalHeadcount}</strong><small>Live CV headcount</small></div></div></div><TrendPanel zone={selectedZone} events={events} /></section>
          <section id="alerts" className={styles.alertSection}><div className={styles.sectionHeader}><div><span className={styles.eyebrow}>RESPONSE QUEUE</span><h2>Active alerts</h2></div><span className={styles.alertCount}>{activeAlerts} open</span></div><div className={styles.alertList}>{alerts.length ? alerts.map((alert) => <article className={styles.alertItem} key={alert.id}><span className={styles.alertIcon} style={{ color: riskMeta[alert.severity].color, background: riskMeta[alert.severity].soft }}><Icon name="bell" /></span><div className={styles.alertBody}><div><strong>{alert.zoneName || `Zone ${alert.zoneId}`}</strong><StatusBadge level={alert.severity} /></div><p>{alert.message}</p><small>{formatTime(alert.timestamp)} · {formatAge(alert.timestamp)}</small></div><button onClick={() => resolveAlert(alert.id)}>Resolve</button></article>) : <div className={styles.emptyPanel}>No active alerts. The command queue is clear.</div>}</div></section>
          <footer id="about" className={styles.footer}><div><a href="#dashboard" className={styles.brand}><span className={styles.brandMark}><Icon name="shield" /></span><span><b>Nirikshan</b><small>AI-powered crowd safety intelligence</small></span></a><p>Nirikshan turns real crowd video signals into explainable early warnings for safer public gatherings.</p></div><div><span className={styles.eyebrow}>ABOUT THE SYSTEM</span><p>Built for TechNova 2026 as a transparent decision-support layer for authorities and communities. Demo inputs use timed replay of curated footage.</p></div><div className={styles.footerStatus}><span className={styles.liveDot} />Backend link {connected ? "connected" : "waiting"}<small>Spring Boot · STOMP · REST</small></div></footer>
          <AdminManagement zones={zones} />
        </>
      )}
    </main>
  );
}

export default function Page() { return <App />; }
