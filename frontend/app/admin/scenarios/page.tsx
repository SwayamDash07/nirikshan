"use client";

import { useEffect, useState } from "react";
import AppShell, { primaryNavItems } from "../../components/AppShell";
import { Button, Card, Spinner } from "../../components/ui";
import { api, clearSession, readSession, type UserInfo } from "../../lib/auth";
import styles from "./scenarios.module.css";

type Zone = { id: number; name: string };
type ScenarioType = "buildup" | "surge" | "persistent_hotspot" | "slowdown" | "recovery" | "normal_one_way" | "slowing_flow" | "reverse_movement" | "conflicting_movement" | "blocked_route" | "alternate_exit_recovery" | "stampede_precursor" | "unusual_behavior" | "ai_crowd_simulation";
type Run = { runId: string; scenarioType: ScenarioType; zoneId: number; status: "PENDING" | "RUNNING" | "COMPLETE" | "STOPPED"; speed: number; message: string; startedAt: string; completedAt?: string };
const navItems = primaryNavItems;
const scenarios: Array<{ value: ScenarioType; label: string; description: string }> = [
  { value: "buildup", label: "Gradual Buildup", description: "Density rises steadily over time." },
  { value: "surge", label: "Sudden Surge", description: "A rapid density spike tests escalation response." },
  { value: "persistent_hotspot", label: "Persistent Hotspot", description: "Sustained localized congestion tests bottleneck handling." },
  { value: "slowdown", label: "Slowdown With Rising Density", description: "Density rises while movement slows, exercising early-warning thresholds." },
  { value: "recovery", label: "Recovery", description: "Risk subsides back to normal and clears stale actions." },
  { value: "normal_one_way", label: "Normal one-way flow", description: "SIMULATION: consistent eastbound movement with sufficient direction data." },
  { value: "slowing_flow", label: "Slowing flow", description: "SIMULATION: movement slows while density rises." },
  { value: "reverse_movement", label: "Reverse movement", description: "SIMULATION: a sustained reversal against the dominant direction." },
  { value: "conflicting_movement", label: "Conflicting movement", description: "SIMULATION: crossing flows compete in one zone." },
  { value: "blocked_route", label: "Blocked route", description: "SIMULATION: the route to Main Gate Exit becomes blocked." },
  { value: "alternate_exit_recovery", label: "Exit-gate recovery", description: "SIMULATION: Main Gate Exit recovers after a route disruption." },
  { value: "stampede_precursor", label: "Stampede precursors", description: "SIMULATION: density accelerates while movement slows, with persistent hotspot and conflicting-flow signals." },
  { value: "unusual_behavior", label: "Unusual behavior", description: "SIMULATION: repeated abrupt slowdowns, reversals, and conflicting movement test persistence gating." },
  { value: "ai_crowd_simulation", label: "AI crowd simulation", description: "BONUS: lightweight agent-based simulation models arrivals, movement, pressure, and local avoidance." },
];

function Simulator({ user }: { user: UserInfo }) {
  const [zones, setZones] = useState<Zone[]>([]);
  const [zoneId, setZoneId] = useState("");
  const [scenarioType, setScenarioType] = useState<ScenarioType>("buildup");
  const [speed, setSpeed] = useState("20");
  const [run, setRun] = useState<Run>();
  const [loading, setLoading] = useState(true);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  useEffect(() => {
    api<Zone[]>("/api/admin/zones").then((next) => { setZones(next); setZoneId(String(next[0]?.id || "")); }).catch((reason) => setError(reason instanceof Error ? reason.message : "Could not load zones.")).finally(() => setLoading(false));
  }, []);
  useEffect(() => {
    if (!run || (run.status !== "PENDING" && run.status !== "RUNNING")) return;
    const timer = window.setInterval(() => api<Run>(`/api/admin/scenarios/${run.runId}/status`).then(setRun).catch(() => undefined), 1000);
    return () => window.clearInterval(timer);
  }, [run]);
  async function start() {
    setWorking(true); setError(""); setNotice("");
    try { const next = await api<Run>("/api/admin/scenarios/run", { method: "POST", body: JSON.stringify({ scenarioType, zoneId: Number(zoneId), speed: Number(speed) }) }); setRun(next); setNotice("Simulation started. The affected zone is marked SIMULATION MODE on the dashboard."); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "Could not start scenario."); }
    finally { setWorking(false); }
  }
  async function stop() {
    if (!run) return;
    setWorking(true); setError("");
    try { const next = await api<Run>(`/api/admin/scenarios/${run.runId}/stop`, { method: "POST" }); setRun(next); setNotice("Simulation stopped. The zone has been restored to its live or offline state."); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "Could not stop scenario."); }
    finally { setWorking(false); }
  }
  if (loading) return <AppShell user={user} title="Scenario Simulator" active="Simulator" navItems={navItems}><Spinner label="Loading simulator" /></AppShell>;
  const active = run?.status === "PENDING" || run?.status === "RUNNING";
  return <AppShell user={user} title="Scenario Simulator" subtitle="Admin-only staff training and system validation drills" active="Simulator" navItems={navItems}>
    <section className={styles.trainingBanner}><strong>DRILL MODE: SIMULATION DATA</strong><p>The Scenario Simulator runs deterministic and AI agent-based crowd events through the same ingestion path as recorded-video telemetry. It is separate from live monitoring and every affected zone is clearly labelled.</p></section>
    {error && <p className={styles.error} role="alert">{error}</p>}{notice && <p className={styles.notice} role="status">{notice}</p>}
    <Card className={styles.panel}>
      <div className={styles.heading}><div><span className={styles.kicker}>CONTROLLED REPLAY</span><h2>Run a safety scenario</h2><p>Use this for staff drills, validation, and reliable product demonstrations.</p></div>{active && <span className={styles.running}><i />{run.status}</span>}</div>
      <div className={styles.formGrid}>
        <label>Zone<select value={zoneId} onChange={(event) => setZoneId(event.target.value)} disabled={active}>{zones.map((zone) => <option key={zone.id} value={zone.id}>{zone.name}</option>)}</select></label>
        <label>Scenario<select value={scenarioType} onChange={(event) => setScenarioType(event.target.value as ScenarioType)} disabled={active}>{scenarios.map((scenario) => <option key={scenario.value} value={scenario.value}>{scenario.label}</option>)}</select></label>
        <label>Replay speed<select value={speed} onChange={(event) => setSpeed(event.target.value)} disabled={active}><option value="5">5x, slow drill</option><option value="10">10x, guided</option><option value="20">20x, quick demo</option><option value="40">40x, validation</option></select></label>
      </div>
      <div className={styles.scenarioDescription}>{scenarios.find((scenario) => scenario.value === scenarioType)?.description}</div>
      <div className={styles.actions}><Button type="button" disabled={working || active || !zoneId} onClick={start}>{working && !active ? "Starting..." : "Run Scenario"}</Button>{active && <Button type="button" variant="danger" disabled={working} onClick={stop}>{working ? "Stopping..." : "Stop Scenario"}</Button>}</div>
    </Card>
    {run && <Card className={styles.statusCard}><div><span className={styles.kicker}>RUN STATUS</span><h2>{scenarios.find((scenario) => scenario.value === run.scenarioType)?.label}</h2><p>Zone {zones.find((zone) => zone.id === run.zoneId)?.name || run.zoneId}, replay speed {run.speed}x</p></div><strong className={run.status === "RUNNING" ? styles.statusRunning : styles.statusDone}>{run.status}</strong><small>{run.message}</small></Card>}
    <Card className={styles.note}><strong>What staff should observe</strong><p>Watch the authority dashboard detect the simulated signal, identify the affected zone, generate a recommendation, hand it off to security, and then clear the action after the recovery scenario. Simulation badges disappear when a run completes or is stopped.</p></Card>
  </AppShell>;
}

export default function Page() {
  const [user, setUser] = useState<UserInfo>();
  useEffect(() => { const session = readSession(); if (!session || session.user.role !== "ADMIN") { clearSession(); window.location.replace("/console/login"); return; } setUser(session.user); }, []);
  if (!user) return <main className={styles.loading}>Checking administrator access</main>;
  return <Simulator user={user} />;
}
