"use client";

import { CSSProperties, useEffect, useRef, useState } from "react";
import { Client, IMessage } from "@stomp/stompjs";
import Icon from "../components/Icon";
import { Button, Card, Spinner } from "../components/ui";
import { api } from "../lib/auth";
import styles from "./console.module.css";

type RiskLevel = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
type Recommendation = {
  id: number;
  zoneId?: number | null;
  zoneName?: string | null;
  type: "REDIRECT" | "DEPLOY_SECURITY" | "OPEN_ROUTE" | "CLOSE_ENTRY" | "ANNOUNCEMENT" | "REASSIGN_PERSONNEL";
  message: string;
  severity: RiskLevel;
  createdAt: string;
  status: "PENDING" | "ACKNOWLEDGED" | "DISMISSED";
  acknowledgedByUserId?: number | null;
  source?: "LIVE" | "SIMULATION";
};

const WS_URL = process.env.NEXT_PUBLIC_WS_URL || "ws://localhost:8080/ws";
const riskMeta: Record<RiskLevel, { label: string; color: string; soft: string }> = {
  LOW: { label: "Normal", color: "var(--risk-low)", soft: "var(--risk-low-soft)" },
  MEDIUM: { label: "Watch", color: "var(--risk-medium)", soft: "var(--risk-medium-soft)" },
  HIGH: { label: "High", color: "var(--risk-high)", soft: "var(--risk-high-soft)" },
  CRITICAL: { label: "Critical", color: "var(--risk-critical)", soft: "var(--risk-critical-soft)" },
};

function age(value: string) {
  const seconds = Math.max(0, Math.round((Date.now() - new Date(value).valueOf()) / 1000));
  return seconds < 60 ? `${seconds}s ago` : `${Math.floor(seconds / 60)}m ago`;
}

function time(value: string) {
  return new Date(value).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });
}

function StatusBadge({ level }: { level: RiskLevel }) {
  const meta = riskMeta[level];
  return <span className={styles.statusBadge} style={{ "--badge-color": meta.color, "--badge-bg": meta.soft } as CSSProperties}><i />{meta.label}</span>;
}

function uniquePending(items: Recommendation[]) {
  const byScope = new Map<string, Recommendation>();
  items.filter((item) => item.status === "PENDING").forEach((item) => {
    const key = item.zoneId == null ? "venue" : String(item.zoneId);
    const existing = byScope.get(key);
    if (!existing || new Date(item.createdAt).valueOf() > new Date(existing.createdAt).valueOf()) byScope.set(key, item);
  });
  return [...byScope.values()].sort((a, b) => new Date(b.createdAt).valueOf() - new Date(a.createdAt).valueOf());
}

export default function ResponseActions() {
  const [items, setItems] = useState<Recommendation[]>([]);
  const [loading, setLoading] = useState(true);
  const [connected, setConnected] = useState(false);
  const [busyId, setBusyId] = useState<number>();
  const [busyAll, setBusyAll] = useState(false);
  const [error, setError] = useState("");
  const [now, setNow] = useState(() => Date.now());
  const stompRef = useRef<Client | null>(null);

  async function load() {
    setLoading(true); setError("");
    try { setItems(uniquePending(await api<Recommendation[]>("/api/recommendations?active=true"))); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "Could not load response actions."); }
    finally { setLoading(false); }
  }

  useEffect(() => {
    load();
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    const client = new Client({
      brokerURL: WS_URL,
      reconnectDelay: 5000,
      onConnect: () => {
        setConnected(true);
        client.subscribe("/topic/recommendations", (message: IMessage) => {
          const next = JSON.parse(message.body) as Recommendation;
          setItems((current) => uniquePending(next.status === "PENDING" ? [next, ...current] : current.filter((item) => item.id !== next.id)));
        });
      },
      onDisconnect: () => setConnected(false),
      onWebSocketError: () => setConnected(false),
      onStompError: () => setConnected(false),
    });
    client.activate();
    stompRef.current = client;
    return () => { client.deactivate(); stompRef.current = null; };
  }, []);

  async function dismiss(id: number) {
    setBusyId(id); setError("");
    try { await api(`/api/recommendations/${id}/dismiss`, { method: "PATCH" }); setItems((current) => current.filter((item) => item.id !== id)); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "Could not dismiss action."); }
    finally { setBusyId(undefined); }
  }

  async function dismissAll() {
    setBusyAll(true); setError("");
    try { await Promise.all(uniquePending(items).map((item) => api(`/api/recommendations/${item.id}/dismiss`, { method: "PATCH" }))); setItems([]); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "Could not dismiss all actions."); await load(); }
    finally { setBusyAll(false); }
  }

  function takeAction(item: Recommendation) {
    const params = new URLSearchParams({ message: `${item.zoneName || "Venue-wide"}: ${item.message}`, recommendationId: String(item.id) });
    if (item.zoneId != null) params.set("zoneId", String(item.zoneId));
    window.location.assign(`/console/admin/actions?${params.toString()}`);
  }

  return <>
    <div className={styles.connection}><span className={connected ? styles.connectedDot : styles.disconnectedDot} />{connected ? "Live command link" : "WebSocket disconnected, reconnecting"}<span>Updated {new Date(now).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}</span></div>
    {loading ? <Spinner label="Loading response actions" /> : error && !items.length ? <div className={styles.errorState}><span className={styles.kicker}>BACKEND UNAVAILABLE</span><h2>Response actions are unavailable</h2><p>{error}</p><Button onClick={load}>Retry connection</Button></div> : <Card className={styles.queueCard}>
      <div className={styles.cardHeader}><div><span className={styles.kicker}>ADMIN RESPONSE QUEUE</span><h2>Response actions</h2><p>One deduplicated recommendation per zone. Review it, send the pre-filled instruction, or dismiss it.</p></div><div className={styles.queueHeaderActions}><span className={styles.queueCount}>{uniquePending(items).length}</span>{items.length > 0 && <Button variant="ghost" size="sm" disabled={busyAll} onClick={dismissAll}>{busyAll ? "Dismissing..." : "Dismiss all"}</Button>}</div></div>
      {error && <p className={styles.status}>{error}</p>}
      <div className={styles.fullAlertList}>{uniquePending(items).length ? uniquePending(items).map((item) => <article className={styles.fullAlert} key={item.id}>
        <span className={`${styles.alertRail} ${styles[`rail${item.severity}`]}`} />
        <div className={styles.fullAlertBody}><div><strong>{item.zoneName || "Venue-wide"} {item.source === "SIMULATION" && <span className={styles.simulationBadge}>SIMULATION</span>}</strong><StatusBadge level={item.severity} /></div><p>{item.message}</p><small>{time(item.createdAt)}, {age(item.createdAt)}, {item.acknowledgedByUserId ? "Sent to security staff" : "Not yet sent to security staff"}</small></div>
        <Button variant="primary" size="sm" onClick={() => takeAction(item)}>Take Action</Button>
        <Button variant="ghost" size="sm" disabled={busyId === item.id || busyAll} onClick={() => dismiss(item.id)}>{busyId === item.id ? "Dismissing..." : "Dismiss"}</Button>
      </article>) : <div className={styles.inlineEmpty}><Icon name="check" /><span>No response action is currently pending.</span></div>}</div>
    </Card>}
  </>;
}
