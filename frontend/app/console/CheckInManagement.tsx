"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Client, IMessage } from "@stomp/stompjs";
import { getCheckIns, triggerCheckIns, type CheckIn } from "../checkin/actions";
import { Button, Card, Spinner } from "../components/ui";
import { readSession } from "../lib/auth";
import styles from "./check-ins.module.css";

const WS_URL = process.env.NEXT_PUBLIC_WS_URL || `${(process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080").replace(/\/$/, "").replace(/^http/, "ws")}/ws`;
type CheckInFilter = "all" | CheckIn["status"];

function formatTime(value?: string | null) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.valueOf()) ? "—" : date.toLocaleString([], { dateStyle: "medium", timeStyle: "short" });
}

export default function CheckInManagement() {
  const [token, setToken] = useState("");
  const [items, setItems] = useState<CheckIn[]>([]);
  const [loading, setLoading] = useState(true);
  const [working, setWorking] = useState(false);
  const [connected, setConnected] = useState(false);
  const [error, setError] = useState("");
  const [filter, setFilter] = useState<CheckInFilter>("all");
  const stompRef = useRef<Client | null>(null);

  const refresh = useCallback(async (silent = false) => {
    if (!token) return;
    if (!silent) setLoading(true);
    try {
      setItems(await getCheckIns(token));
      setError("");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Could not load check-ins");
    } finally {
      if (!silent) setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    const session = readSession();
    if (session?.user.role === "ADMIN") setToken(session.token);
  }, []);

  useEffect(() => {
    if (!token) return;
    void refresh();
    const client = new Client({
      brokerURL: WS_URL,
      reconnectDelay: 5000,
      onConnect: () => {
        setConnected(true);
        client.subscribe("/topic/check-ins", (message: IMessage) => {
          const next = JSON.parse(message.body) as CheckIn;
          setItems((current) => [next, ...current.filter((item) => item.id !== next.id)].sort((left, right) => new Date(right.triggeredAt).valueOf() - new Date(left.triggeredAt).valueOf()));
        });
      },
      onDisconnect: () => setConnected(false),
      onWebSocketError: () => setConnected(false),
      onStompError: () => setConnected(false),
    });
    client.activate();
    stompRef.current = client;
    return () => { client.deactivate(); stompRef.current = null; };
  }, [refresh, token]);

  async function trigger() {
    if (!token) return;
    setWorking(true);
    setError("");
    try {
      await triggerCheckIns(token);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Could not trigger check-ins");
    } finally {
      setWorking(false);
    }
  }

  const filteredItems = filter === "all" ? items : items.filter((item) => item.status === filter);
  const pendingCount = items.filter((item) => item.status === "pending").length;
  const confirmedCount = items.filter((item) => item.status === "confirmed").length;
  const filterLabel = filter === "pending" ? "pending" : filter === "confirmed" ? "confirmed" : "matching";

  return <Card className={styles.card}>
    <div className={styles.header}>
      <div><span className={styles.kicker}>MANUAL SAFETY CONFIRMATION</span><h2>Staff check-ins</h2><p>Trigger a confirmation request for every active security staff member and monitor responses.</p></div>
      <div className={styles.headerTools}>
        <span className={`${styles.connection} ${connected ? styles.connectionLive : styles.connectionOffline}`}><i />{connected ? "Live updates" : "Connecting live updates"}</span>
        <label className={styles.filter} htmlFor="check-in-filter"><span>Show</span><select id="check-in-filter" value={filter} onChange={(event) => setFilter(event.target.value as CheckInFilter)}><option value="all">All ({items.length})</option><option value="pending">Not responded ({pendingCount})</option><option value="confirmed">Responded ({confirmedCount})</option></select></label>
        <Button onClick={trigger} disabled={working || loading}>{working ? "Triggering..." : "Trigger Check-In"}</Button>
      </div>
    </div>
    {error && <div className={styles.error} role="alert">{error}</div>}
    {loading ? <Spinner label="Loading staff check-ins" /> : items.length ? filteredItems.length ? <div className={styles.tableWrap}><table className={styles.table}><thead><tr><th>Staff member</th><th>Status</th><th>Triggered at</th><th>Responded at</th></tr></thead><tbody>{filteredItems.map((item) => <tr className={item.status === "pending" ? styles.pending : ""} key={item.id}><td>{item.staffName}</td><td><span className={`${styles.status} ${item.status === "pending" ? styles.statusPending : styles.statusConfirmed}`}>{item.status === "pending" ? "Pending" : "Confirmed"}</span></td><td>{formatTime(item.triggeredAt)}</td><td>{formatTime(item.respondedAt)}</td></tr>)}</tbody></table></div> : <div className={styles.empty}><strong>No {filterLabel} check-ins</strong><span>Try another response filter to see the available staff check-ins.</span></div> : <div className={styles.empty}><strong>No check-ins have been triggered</strong><span>Use Trigger Check-In to request a safety confirmation from active staff.</span></div>}
  </Card>;
}
