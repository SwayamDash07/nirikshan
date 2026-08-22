"use client";

import { useEffect, useMemo, useState } from "react";
import { Client, IMessage } from "@stomp/stompjs";
import AppShell, { NavItem } from "../../components/AppShell";
import { Card, Button, Spinner } from "../../components/ui";
import { clearSession, readSession, type Session } from "../../lib/auth";
import { confirmStaffCheckIn, getStaffCheckIn, type CheckIn } from "../actions";
import styles from "../checkin.module.css";

const WS_URL = process.env.NEXT_PUBLIC_WS_URL || `${(process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080").replace(/\/$/, "").replace(/^http/, "ws")}/ws`;

function formatTime(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.valueOf()) ? "Not available" : date.toLocaleString([], { dateStyle: "medium", timeStyle: "short" });
}

export default function Page({ params }: { params: { staffName: string } }) {
  const routeName = useMemo(() => decodeURIComponent(params.staffName), [params.staffName]);
  const navItems = useMemo<NavItem[]>(() => [
    { label: "Assigned zone", href: "/security", icon: "grid" },
    { label: "Citizen reports", href: "/security/reports", icon: "activity" },
    { label: "Instructions", href: "/security#instructions", icon: "activity" },
    { label: "Check-in", href: `/checkin/${encodeURIComponent(routeName)}`, icon: "check" },
    { label: "Security", href: "/alerts/security", icon: "lock" },
  ], [routeName]);
  const [session, setSession] = useState<Session>();
  const [checkIn, setCheckIn] = useState<CheckIn | null>();
  const [loading, setLoading] = useState(true);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    const current = readSession();
    if (!current || current.user.role !== "SECURITY") {
      clearSession();
      window.location.replace("/alerts");
      return;
    }
    if (current.user.mustChangePassword) {
      window.location.replace("/alerts/security");
      return;
    }
    if (current.user.name.toLowerCase() !== routeName.toLowerCase()) {
      window.location.replace(`/checkin/${encodeURIComponent(current.user.name)}`);
      return;
    }
    setSession(current);
    getStaffCheckIn(current.token, routeName).then(setCheckIn).catch((reason) => setError(reason instanceof Error ? reason.message : "Could not load the check-in")).finally(() => setLoading(false));
    const client = new Client({
      brokerURL: WS_URL,
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe("/topic/check-ins", (message: IMessage) => {
          const next = JSON.parse(message.body) as CheckIn;
          if (next.staffName.toLowerCase() !== routeName.toLowerCase()) return;
          setCheckIn(next.status === "pending" ? next : null);
        });
      },
    });
    client.activate();
    return () => { client.deactivate(); };
  }, [routeName]);

  async function confirm() {
    if (!session || !checkIn) return;
    setWorking(true);
    setError("");
    try {
      await confirmStaffCheckIn(session.token, routeName);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Could not confirm the check-in");
    } finally {
      setWorking(false);
    }
  }

  if (!session) return <main className={styles.state}>Checking staff access</main>;
  return <AppShell user={session.user} title="Safety check-in" subtitle="Confirm your current status for the command team" active="Check-in" navItems={navItems}>
    <main className={styles.page}>
      {loading ? <Spinner label="Loading current check-in" /> : <Card className={styles.card}>
        <span className={styles.kicker}>MANUAL SAFETY CONFIRMATION</span>
        <h2>{checkIn ? "Please confirm you are safe" : "No active check-in"}</h2>
        {error ? <div className={styles.error} role="alert">{error}</div> : checkIn ? <><p>The command team requested a safety confirmation for <strong>{checkIn.staffName}</strong>.</p><div className={styles.meta}><span>Triggered at</span><strong>{formatTime(checkIn.triggeredAt)}</strong></div><Button size="lg" onClick={confirm} disabled={working}>{working ? "Confirming..." : "All Clear"}</Button></> : <p className={styles.neutral}>There is no pending safety confirmation for you right now.</p>}
      </Card>}
    </main>
  </AppShell>;
}
