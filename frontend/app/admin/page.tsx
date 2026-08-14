"use client";

import { ChangeEvent, DragEvent, useCallback, useEffect, useState } from "react";
import { Client, IMessage } from "@stomp/stompjs";
import AppShell, { NavItem } from "../components/AppShell";
import Icon from "../components/Icon";
import { Button, Card, Spinner } from "../components/ui";
import { api, apiBase, clearSession, readSession, type UserInfo } from "../lib/auth";
import styles from "./admin.module.css";

type RiskLevel = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
type FeedStatus = "OFFLINE" | "LIVE";
type Zone = {
  id: number;
  name: string;
  currentDensity: number;
  currentPeopleCount: number;
  currentRiskLevel: RiskLevel;
  lastUpdated: string;
  feedStatus: FeedStatus;
  videoFilename?: string;
  videoUrl?: string;
  feedStartedAt?: string;
  currentLoopIteration: number;
};
type RiskEvent = { zoneId: number; timestamp: string; densityScore: number; peopleCount: number; riskLevel: RiskLevel };

const WS_URL = process.env.NEXT_PUBLIC_WS_URL || "ws://localhost:8080/ws";
const navItems: NavItem[] = [
  { label: "Dashboard", href: "/console", icon: "grid" },
  { label: "Administration", href: "/console/admin", icon: "users" },
  { label: "Video ingestion", href: "/admin", icon: "upload" },
  { label: "Security", href: "/alerts/security", icon: "lock" },
];
const riskLabels: Record<RiskLevel, string> = { LOW: "Normal", MEDIUM: "Watch", HIGH: "High", CRITICAL: "Critical" };

function formatTime(value?: string) {
  if (!value) return "Awaiting signal";
  const date = new Date(value);
  return Number.isNaN(date.valueOf()) ? "Awaiting signal" : date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });
}

function CameraPlaceholder() {
  return <div className={styles.cameraPlaceholder}><Icon name="camera" /><span>No footage connected</span></div>;
}

function CameraCard({ zone, busy, onPick, onDrop, onStop }: { zone: Zone; busy: boolean; onPick: (file: File) => void; onDrop: (file: File) => void; onStop: () => void }) {
  const live = zone.feedStatus === "LIVE";
  function handleFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (file) onPick(file);
    event.target.value = "";
  }
  function handleDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault();
    const file = event.dataTransfer.files?.[0];
    if (file) onDrop(file);
  }
  return <Card className={`${styles.cameraCard} ${live ? styles.liveCard : ""}`} onDragOver={(event) => event.preventDefault()} onDrop={handleDrop}>
    <div className={styles.cameraHeader}>
      <div><span className={styles.zoneLabel}>ZONE {String(zone.id).padStart(2, "0")}</span><h2>{zone.name}</h2></div>
      <span className={`${styles.feedBadge} ${live ? styles.feedLive : styles.feedOffline}`}><i />{live ? "Live" : "Offline"}</span>
    </div>
    <div className={styles.preview}>
      {live && zone.videoUrl ? <video key={zone.videoUrl} src={`${apiBase}${zone.videoUrl}`} autoPlay muted loop playsInline preload="auto" aria-label={`${zone.name} camera preview`} /> : <CameraPlaceholder />}
      {live && <span className={styles.previewOverlay}><i className={styles.pulseDot} />PROCESSING NOW</span>}
    </div>
    {live ? <>
      <div className={styles.liveStats}>
        <div><span>Headcount</span><strong>{zone.currentPeopleCount ?? 0}</strong></div>
        <div><span>Density</span><strong>{(zone.currentDensity ?? 0).toFixed(2)} <small>/m²</small></strong></div>
        <div><span>Risk</span><strong className={`${styles.riskText} ${styles[`risk${zone.currentRiskLevel}`]}`}>{riskLabels[zone.currentRiskLevel]}</strong></div>
      </div>
      <div className={styles.cardFooter}><span>Updated {formatTime(zone.lastUpdated)}</span><span>Loop {zone.currentLoopIteration}</span><Button variant="danger" size="sm" type="button" disabled={busy} onClick={onStop}>{busy ? "Stopping..." : "Stop Coverage"}</Button></div>
    </> : <div className={styles.offlineBody}><p>Connect a recording to bring this camera online. The footage will loop continuously as a live simulation.</p><label className={styles.dropHint} htmlFor={`feed-file-${zone.id}`}>Drop footage anywhere on this card or choose a file</label><input id={`feed-file-${zone.id}`} className={styles.hiddenInput} type="file" accept="video/*,.mp4,.mov,.avi,.mkv" onChange={handleFile} /><Button type="button" disabled={busy} onClick={() => document.getElementById(`feed-file-${zone.id}`)?.click()}><Icon name="upload" />{busy ? "Connecting camera feed..." : "Connect Footage"}</Button></div>}
  </Card>;
}

function AdminUpload({ user }: { user: UserInfo }) {
  const [zones, setZones] = useState<Zone[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyZoneId, setBusyZoneId] = useState<number>();
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const loadZones = useCallback(async () => {
    const next = await api<Zone[]>("/api/admin/zones");
    setZones(next);
  }, []);

  useEffect(() => {
    if (user.mustChangePassword) { window.location.replace("/alerts/security"); return; }
    loadZones().catch((reason) => setError(reason instanceof Error ? reason.message : "Could not load camera coverage.")).finally(() => setLoading(false));
  }, [loadZones, user.mustChangePassword]);

  useEffect(() => {
    if (loading) return;
    const timer = window.setInterval(() => loadZones().catch(() => undefined), 4000);
    return () => window.clearInterval(timer);
  }, [loadZones, loading]);

  useEffect(() => {
    const client = new Client({ brokerURL: WS_URL, reconnectDelay: 5000, onConnect: () => {
      client.subscribe("/topic/risk-updates", (message: IMessage) => {
        const event = JSON.parse(message.body) as RiskEvent;
        setZones((current) => current.map((zone) => zone.id === event.zoneId ? { ...zone, currentDensity: event.densityScore, currentPeopleCount: event.peopleCount, currentRiskLevel: event.riskLevel, lastUpdated: event.timestamp } : zone));
      });
    } });
    client.activate();
    return () => { client.deactivate(); };
  }, []);

  async function connect(zoneId: number, file: File) {
    setBusyZoneId(zoneId); setError(""); setNotice("");
    try {
      const body = new FormData(); body.append("file", file);
      const connected = await api<Zone>(`/api/admin/zones/${zoneId}/connect-footage`, { method: "POST", body });
      setZones((current) => current.map((zone) => zone.id === zoneId ? connected : zone));
      setNotice(`${connected.name} is online. Continuous coverage is now running.`);
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Could not connect camera footage."); }
    finally { setBusyZoneId(undefined); }
  }

  async function stop(zoneId: number) {
    setBusyZoneId(zoneId); setError(""); setNotice("");
    try {
      const stopped = await api<Zone>(`/api/admin/zones/${zoneId}/stop-coverage`, { method: "POST" });
      setZones((current) => current.map((zone) => zone.id === zoneId ? stopped : zone));
      setNotice(`${stopped.name} coverage stopped. The camera is offline.`);
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Could not stop camera coverage."); }
    finally { setBusyZoneId(undefined); }
  }

  if (loading) return <AppShell user={user} title="Video ingestion" active="Video ingestion" navItems={navItems}><Spinner label="Loading camera coverage" /></AppShell>;
  return <AppShell user={user} title="Video ingestion" subtitle="Connect recorded footage as continuous simulated CCTV coverage" active="Video ingestion" navItems={navItems}>
    <section className={styles.intro}><div><span className={styles.kicker}>CAMERA MANAGEMENT</span><h1>Connect your camera network</h1><p>Each zone behaves like a security camera. Connect footage once and Nirikshan keeps processing it in a real-time loop until you stop coverage.</p></div><div className={styles.networkState}><i />{zones.filter((zone) => zone.feedStatus === "LIVE").length} of {zones.length} cameras live</div></section>
    {notice && <p className={styles.notice} role="status">{notice}</p>}
    {error && <p className={styles.error} role="alert">{error}</p>}
    <section className={styles.cameraGrid} aria-label="Camera coverage by zone">{zones.map((zone) => <CameraCard key={zone.id} zone={zone} busy={busyZoneId === zone.id} onPick={(file) => connect(zone.id, file)} onDrop={(file) => connect(zone.id, file)} onStop={() => stop(zone.id)} />)}</section>
    <Card className={styles.simulationNote}><span className={styles.noteIcon}><Icon name="activity" /></span><div><strong>Live simulation mode</strong><p>Uploaded recordings are looped from frame 0. Every signal is timestamped with the current wall-clock time, so the map, heatmap, zone cards, and trend charts stay current for demonstrations.</p></div></Card>
  </AppShell>;
}

export default function Page() {
  const [user, setUser] = useState<UserInfo>();
  useEffect(() => { const session = readSession(); if (!session || session.user.role !== "ADMIN") { clearSession(); window.location.replace("/console/login"); return; } setUser(session.user); }, []);
  if (!user) return <main className={styles.state}>Checking access</main>;
  return <AdminUpload user={user} />;
}
