"use client";

import dynamic from "next/dynamic";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Client, IMessage } from "@stomp/stompjs";
import styles from "./citizen.module.css";

const CitizenMiniMap = dynamic(() => import("./CitizenMiniMap"), { ssr: false, loading: () => <div className={styles.mapLoading}>Loading safety map…</div> });
const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";
const WS_URL = process.env.NEXT_PUBLIC_WS_URL || "ws://localhost:8080/ws";

type RiskLevel = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
type Zone = { id: number; name: string; latitude: number; longitude: number; currentDensity: number; currentRiskLevel: RiskLevel; lastUpdated: string };
type Venue = { id: number; name: string };
type Alert = { id: number; zoneId: number; zoneName?: string; timestamp: string; message: string; severity: RiskLevel; resolved: boolean };
type Point = { lat: number; lng: number };

const colors: Record<RiskLevel, string> = { LOW: "#2caa72", MEDIUM: "#d69a16", HIGH: "#e97825", CRITICAL: "#d84b54" };
const labels: Record<RiskLevel, string> = { LOW: "Normal", MEDIUM: "Watch", HIGH: "High", CRITICAL: "Critical" };

async function getJson<T>(path: string): Promise<T> { const response = await fetch(`${API_BASE}${path}`, { cache: "no-store" }); if (!response.ok) throw new Error(`Backend returned ${response.status}`); return response.json(); }
function relativeTime(value: string) { const seconds = Math.max(0, Math.round((Date.now() - new Date(value).valueOf()) / 1000)); if (seconds < 60) return "just now"; if (seconds < 3600) return `${Math.floor(seconds / 60)} min ago`; return `${Math.floor(seconds / 3600)} hr ago`; }
function distanceMeters(a: Point, b: Point) { const earth = 6371000; const radians = (n: number) => n * Math.PI / 180; const dLat = radians(b.lat - a.lat); const dLng = radians(b.lng - a.lng); const x = Math.sin(dLat / 2) ** 2 + Math.cos(radians(a.lat)) * Math.cos(radians(b.lat)) * Math.sin(dLng / 2) ** 2; return Math.round(earth * 2 * Math.atan2(Math.sqrt(x), Math.sqrt(1 - x))); }
function distanceLabel(meters: number) { return meters < 1000 ? `${meters}m away` : `${(meters / 1000).toFixed(1)}km away`; }

export default function CitizenAlertsPage() {
  const [venue, setVenue] = useState<Venue>();
  const [zones, setZones] = useState<Zone[]>([]);
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [location, setLocation] = useState<Point>();
  const [locationState, setLocationState] = useState<"requesting" | "granted" | "denied" | "unavailable">("requesting");
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");
  const [description, setDescription] = useState("");
  const [reportZoneId, setReportZoneId] = useState<number>();
  const [reportState, setReportState] = useState("");
  const [reporting, setReporting] = useState(false);

  const load = useCallback(async (silent = false) => {
    if (silent) setRefreshing(true); else setLoading(true);
    setError("");
    try {
      const venues = await getJson<Venue[]>("/api/venues");
      if (!venues.length) throw new Error("No venue is available yet.");
      const currentVenue = venues[0];
      const [currentZones, currentAlerts] = await Promise.all([getJson<Zone[]>(`/api/venues/${currentVenue.id}/zones`), getJson<Alert[]>("/api/alerts?active=true")]);
      setVenue(currentVenue); setZones(currentZones); setAlerts(currentAlerts); setReportZoneId((current) => current || currentZones[0]?.id);
    } catch (err) { setError(err instanceof Error ? err.message : "Could not load safety alerts."); }
    finally { setLoading(false); setRefreshing(false); }
  }, []);

  useEffect(() => { load(); if (!navigator.geolocation) { setLocationState("unavailable"); return; } navigator.geolocation.getCurrentPosition((position) => { setLocation({ lat: position.coords.latitude, lng: position.coords.longitude }); setLocationState("granted"); }, () => setLocationState("denied"), { enableHighAccuracy: false, timeout: 8000 }); }, [load]);

  useEffect(() => { const client = new Client({ brokerURL: WS_URL, reconnectDelay: 5000, onConnect: () => client.subscribe("/topic/alerts", (message: IMessage) => { const incoming = JSON.parse(message.body) as Alert; setAlerts((current) => incoming.resolved ? current.filter((item) => item.id !== incoming.id) : [incoming, ...current.filter((item) => item.id !== incoming.id)]); }) }); client.activate(); return () => { client.deactivate(); }; }, []);

  const displayedAlerts = useMemo(() => [...alerts].sort((a, b) => location ? distanceMeters(location, zonePoint(zones, a.zoneId)) - distanceMeters(location, zonePoint(zones, b.zoneId)) : new Date(b.timestamp).valueOf() - new Date(a.timestamp).valueOf()), [alerts, location, zones]);
  const nearestZone = useMemo(() => location && zones.length ? [...zones].sort((a, b) => distanceMeters(location, { lat: a.latitude, lng: a.longitude }) - distanceMeters(location, { lat: b.latitude, lng: b.longitude }))[0] : zones[0], [location, zones]);

  async function submitReport(event: React.FormEvent) { event.preventDefault(); if (!reportZoneId || !description.trim()) return; setReporting(true); setReportState(""); try { const response = await fetch(`${API_BASE}/api/citizen-reports`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ zoneId: reportZoneId, description: description.trim() }) }); if (!response.ok) throw new Error("Could not submit report"); setDescription(""); setReportState("Thanks — your report was sent to the safety team."); } catch (err) { setReportState(err instanceof Error ? err.message : "Could not submit report."); } finally { setReporting(false); } }

  return <main className={styles.page}><header className={styles.header}><a href="/" className={styles.brand}><span>✓</span><b>Nirikshan</b></a><span className={styles.headerTag}>CAMPUS SAFETY</span><a href="/" className={styles.commandLink}>Command view →</a></header><section className={styles.welcome}><span className={styles.eyebrow}>{venue?.name || "NIRIKSHAN ALERTS"}</span><h1>Stay informed.<br /><em>Move safely.</em></h1><p>Live safety alerts for your campus, with calm guidance when it matters.</p><div className={styles.locationPill}>{locationState === "granted" ? "● Location enabled" : locationState === "requesting" ? "○ Finding your location…" : "○ Showing all campus alerts"}<button onClick={() => load(true)} disabled={refreshing}>{refreshing ? "Refreshing…" : "Refresh"}</button></div></section>{loading ? <div className={styles.centerState}>Loading campus alerts…</div> : error ? <div className={styles.error}>{error}<button onClick={() => load()}>Try again</button></div> : <><CitizenMiniMap zones={zones} alerts={alerts} location={location} /><section className={styles.alertSection}><div className={styles.sectionTitle}><div><span className={styles.eyebrow}>SAFETY FEED</span><h2>{displayedAlerts.length ? "Active alerts near you" : "All clear for now"}</h2></div><span className={styles.liveBadge}>LIVE</span></div>{displayedAlerts.length ? displayedAlerts.map((alert) => { const zone = zones.find((item) => item.id === alert.zoneId); const distance = location && zone ? distanceMeters(location, { lat: zone.latitude, lng: zone.longitude }) : undefined; return <article className={styles.alertCard} key={alert.id}><div className={styles.alertTop}><span className={styles.severity} style={{ color: colors[alert.severity], background: `${colors[alert.severity]}16` }}><i style={{ background: colors[alert.severity] }} />{labels[alert.severity]}</span>{distance !== undefined && <span className={styles.distance}>{distanceLabel(distance)}</span>}</div><h3>{alert.zoneName || zone?.name || `Zone ${alert.zoneId}`}</h3><p>{alert.message}</p><span className={styles.alertTime}>{relativeTime(alert.timestamp)}</span></article>; }) : <div className={styles.safeCard}><span>✓</span><div><strong>No active safety alerts</strong><p>Continue following campus guidance and stay aware of your surroundings.</p></div></div>}</section><section className={styles.reportCard}><div><span className={styles.eyebrow}>COMMUNITY SIGNAL</span><h2>See something? Let us know.</h2><p>Share a safety concern with the campus response team.</p></div><form onSubmit={submitReport}><label>Nearest area<select value={reportZoneId || ""} onChange={(event) => setReportZoneId(Number(event.target.value))}>{zones.map((zone) => <option key={zone.id} value={zone.id}>{zone.name}</option>)}</select></label><label>What happened?<textarea value={description} onChange={(event) => setDescription(event.target.value)} placeholder="Briefly describe the issue…" rows={3} maxLength={2000} required /></label><button className={styles.submitButton} disabled={reporting || !description.trim()}>{reporting ? "Sending…" : "Send report"}</button>{reportState && <p className={styles.reportState}>{reportState}</p>}</form></section></>}<footer className={styles.footer}><span>Powered by Nirikshan</span><small>Alerts are decision support. In an emergency, contact campus security or local emergency services.</small></footer></main>;
}

function zonePoint(zones: Zone[], zoneId: number): Point { const zone = zones.find((item) => item.id === zoneId); return zone ? { lat: zone.latitude, lng: zone.longitude } : { lat: 20.3641, lng: 85.8163 }; }

