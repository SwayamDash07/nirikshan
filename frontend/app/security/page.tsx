"use client";

import { useCallback, useEffect, useState } from "react";
import AppShell, { NavItem } from "../components/AppShell";
import { Button, Card, Spinner } from "../components/ui";
import { api, clearSession, readSession, type UserInfo } from "../lib/auth";
import styles from "./security.module.css";

type RiskLevel = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
type SecurityAlert = {
  id: number;
  zoneId: number;
  zoneName: string;
  latitude: number;
  longitude: number;
  timestamp: string;
  message: string;
  severity: RiskLevel;
  resolved: boolean;
  source?: "LIVE" | "SIMULATION";
};
type Instruction = { id: number; message: string; createdAt: string };
const labels: Record<RiskLevel, string> = {
  LOW: "Normal",
  MEDIUM: "Watch",
  HIGH: "High",
  CRITICAL: "Critical",
};
const navItems: NavItem[] = [
  { label: "Assigned zone", href: "/security", icon: "grid" },
  { label: "Citizen reports", href: "/security/reports", icon: "activity" },
  { label: "Instructions", href: "/security#instructions", icon: "activity" },
  { label: "Security", href: "/alerts/security", icon: "lock" },
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

function SecurityWorkspace({
  user,
  shellUser,
  preview,
}: {
  user: UserInfo;
  shellUser: UserInfo;
  preview: boolean;
}) {
  const [alerts, setAlerts] = useState<SecurityAlert[]>([]);
  const [instructions, setInstructions] = useState<Instruction[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [nextAlerts, nextInstructions] = await Promise.all([
        api<SecurityAlert[]>("/api/security/alerts"),
        api<Instruction[]>("/api/security/instructions"),
      ]);
      setAlerts(nextAlerts);
      setInstructions(nextInstructions);
    } catch (reason) {
      setError(
        reason instanceof Error
          ? reason.message
          : "Could not load assigned zone data",
      );
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => {
    if (preview) {
      setLoading(false);
      return;
    }
    if (user.mustChangePassword) {
      window.location.replace("/alerts/security");
      return;
    }
    load();
  }, [load, user.mustChangePassword, preview]);
  async function acknowledge(id: number) {
    try {
      await api(`/api/security/alerts/${id}/acknowledge`, { method: "POST" });
      setAlerts((current) => current.filter((alert) => alert.id !== id));
    } catch (reason) {
      setError(
        reason instanceof Error
          ? reason.message
          : "Could not acknowledge alert",
      );
    }
  }
  if (loading)
    return (
      <AppShell
        user={shellUser}
        previewRole={preview ? "SECURITY" : undefined}
        title="Assigned zone"
        active="Assigned zone"
        navItems={navItems}
      >
        <Spinner label="Loading assigned zone" />
      </AppShell>
    );
  return (
    <AppShell
      user={shellUser}
      previewRole={preview ? "SECURITY" : undefined}
      title={user.assignedZoneName || "Assigned zone"}
      subtitle="Operational alerts and instructions from the command team"
      active="Assigned zone"
      navItems={navItems}
    >
      <div className={styles.toolbar}>
        <span className={styles.liveState}>
          <i />
          {preview ? "Security preview" : "Staff workspace"}
        </span>
        {!preview && (
          <Button variant="secondary" size="sm" onClick={load}>
            Refresh
          </Button>
        )}
      </div>
      {preview && (
        <div className={styles.previewBanner} role="status">
          Preview mode is active. Security API actions are disabled.
        </div>
      )}
      {error && (
        <div className={styles.error} role="alert">
          {error}
        </div>
      )}
      {!error && alerts.some((alert) => alert.source === "SIMULATION") && <div className={styles.simulationBanner} role="status">SIMULATION MODE: This alert is deterministic drill data, not a live camera alert.</div>}
      <section className={styles.securityGrid}>
        <Card className={styles.queue}>
          <div className={styles.cardHeader}>
            <div>
              <span className={styles.kicker}>RESPONSE QUEUE</span>
              <h2>Alerts for your zone</h2>
              <p>Acknowledge each alert after it has been handled.</p>
            </div>
            <span className={styles.count}>{alerts.length}</span>
          </div>
          {alerts.length ? (
            <div className={styles.alertList}>
              {alerts.map((alert) => (
                <article className={styles.alert} key={alert.id}>
                  <div
                    className={`${styles.rail} ${styles[`rail${alert.severity}`]}`}
                  />
                  <div className={styles.alertBody}>
                    <div className={styles.alertMeta}>
                      <span
                        className={`${styles.severity} ${styles[`severity${alert.severity}`]}`}
                      >
                        {labels[alert.severity]}
                      </span>
                      {alert.source === "SIMULATION" && <span className={styles.simulationBadge}>SIMULATION</span>}
                      <small>{ago(alert.timestamp)}</small>
                    </div>
                    <h3>{alert.zoneName}</h3>
                    <p>{alert.message}</p>
                    <small>
                      {alert.latitude.toFixed(5)}, {alert.longitude.toFixed(5)}
                    </small>
                  </div>
                  <Button size="sm" onClick={() => acknowledge(alert.id)}>
                    Acknowledge
                  </Button>
                </article>
              ))}
            </div>
          ) : (
            <div className={styles.empty}>
              <strong>No active alerts</strong>
              <span>
                {preview
                  ? "Preview data is not loaded for this workspace."
                  : "Your assigned zone is clear right now."}
              </span>
            </div>
          )}
        </Card>
        <Card className={styles.instructions} id="instructions">
          <div className={styles.cardHeader}>
            <div>
              <span className={styles.kicker}>COMMAND NOTES</span>
              <h2>Instructions</h2>
              <p>Latest guidance from the command team.</p>
            </div>
          </div>
          {instructions.length ? (
            <div className={styles.instructionList}>
              {instructions.map((instruction) => (
                <article key={instruction.id}>
                  <span className={styles.instructionIndex}>
                    {String(instruction.id).padStart(2, "0")}
                  </span>
                  <div>
                    <strong>{instruction.message}</strong>
                    <small>{ago(instruction.createdAt)}</small>
                  </div>
                </article>
              ))}
            </div>
          ) : (
            <div className={styles.empty}>
              <strong>No instructions</strong>
              <span>
                {preview
                  ? "Preview data is not loaded for this workspace."
                  : "There is no new guidance at this time."}
              </span>
            </div>
          )}
        </Card>
      </section>
    </AppShell>
  );
}

export default function Page() {
  const [access, setAccess] = useState<{
    user: UserInfo;
    shellUser: UserInfo;
    preview: boolean;
  }>();
  useEffect(() => {
    const session = readSession();
    const preview =
      new URLSearchParams(window.location.search).get("preview") === "security";
    if (!session) {
      clearSession();
      window.location.replace("/alerts");
      return;
    }
    if (session.user.role === "ADMIN" && preview) {
      setAccess({
        user: {
          ...session.user,
          role: "SECURITY",
          assignedZoneName:
            session.user.assignedZoneName || "Test security zone",
          mustChangePassword: false,
        },
        shellUser: session.user,
        preview: true,
      });
      return;
    }
    if (session.user.role !== "SECURITY") {
      if (session.user.role === "ADMIN") window.location.replace("/console");
      else window.location.replace("/alerts");
      return;
    }
    setAccess({ user: session.user, shellUser: session.user, preview: false });
  }, []);
  if (!access) return <main className={styles.state}>Checking access</main>;
  return (
    <SecurityWorkspace
      user={access.user}
      shellUser={access.shellUser}
      preview={access.preview}
    />
  );
}
