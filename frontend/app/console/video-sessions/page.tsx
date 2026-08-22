"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import AppShell, { primaryNavItems } from "../../components/AppShell";
import { Card, Spinner } from "../../components/ui";
import { clearSession, readSession, type UserInfo } from "../../lib/auth";
import { getRecordedSessions, type RecordedRiskLevel, type RecordedSession, type RecordedTelemetry } from "./actions";
import styles from "./video-sessions.module.css";

const riskLabels: Record<RecordedRiskLevel, string> = { LOW: "Normal", MEDIUM: "Watch", HIGH: "High", CRITICAL: "Critical" };

function formatTime(seconds: number) {
  const total = Math.max(0, Math.floor(seconds));
  return `${String(Math.floor(total / 60)).padStart(2, "0")}:${String(total % 60).padStart(2, "0")}`;
}

function activeTelemetry(telemetry: RecordedTelemetry[], currentTime: number) {
  if (!telemetry.length) return undefined;
  return telemetry.reduce((selected, item) => item.secondsIntoClip <= currentTime ? item : selected, telemetry[0]);
}

function RiskBadge({ level }: { level: RecordedRiskLevel }) {
  return <span className={`${styles.riskBadge} ${styles[`risk${level}`]}`}><i />{riskLabels[level]}</span>;
}

function TelemetryCard({ label, value, detail }: { label: string; value: string; detail: string }) {
  return <div className={styles.telemetryCard}><span>{label}</span><strong>{value}</strong><small>{detail}</small></div>;
}

function RecordedTelemetryCards({ session, sample }: { session: RecordedSession; sample?: RecordedTelemetry }) {
  return <div className={styles.telemetryGrid} aria-label={`${session.zoneName} recorded telemetry`}>
    <TelemetryCard label="Headcount" value={String(sample?.peopleCount ?? "—")} detail="People detected" />
    <TelemetryCard label="Density" value={sample ? `${sample.densityScore.toFixed(2)} / m²` : "—"} detail="Recorded density" />
    <div className={styles.telemetryCard}><span>Risk</span>{sample ? <RiskBadge level={sample.riskLevel} /> : <strong>—</strong>}<small>{sample ? sample.riskLevel : "Awaiting telemetry"}</small></div>
  </div>;
}

function RecordedSessionCard({ session }: { session: RecordedSession }) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [currentTime, setCurrentTime] = useState(0);
  const [videoFailed, setVideoFailed] = useState(false);
  const sample = useMemo(() => activeTelemetry(session.telemetry, currentTime), [currentTime, session.telemetry]);
  const updateFromVideo = () => {
    const time = videoRef.current?.currentTime;
    if (time != null) setCurrentTime(time);
  };

  return <article className={styles.sessionCard}>
    <div className={styles.sessionHeader}>
      <div><span className={styles.kicker}>ZONE {String(session.zoneId).padStart(2, "0")}</span><h2>{session.zoneName}</h2><p>{session.cameraLabel}</p></div>
      <span className={styles.privacyBadge}>Recorded Session · Privacy Mode Active</span>
    </div>
    <div className={styles.videoFrame}>
      <video ref={videoRef} className={styles.video} src={session.videoUrl || undefined} autoPlay loop muted playsInline controls preload="metadata" onTimeUpdate={updateFromVideo} onLoadedMetadata={updateFromVideo} onError={() => setVideoFailed(true)} aria-label={`Recorded privacy-safe footage for ${session.zoneName}`} />
      {(!session.videoAvailable || videoFailed) && <div className={styles.videoUnavailable}><strong>Recorded video unavailable</strong><span>Check that the privacy-safe recording is available from the backend.</span></div>}
      {sample && <div className={styles.videoOverlay}><span>{formatTime(currentTime)} · RECORDED</span><strong>{sample.peopleCount} people</strong><small>{sample.densityScore.toFixed(2)} people / m² · {riskLabels[sample.riskLevel]}</small></div>}
    </div>
    <RecordedTelemetryCards session={session} sample={sample} />
    <div className={styles.sessionFooter}><span>{sample ? sample.explanation : session.telemetrySource}</span><span>{session.telemetry.length ? `${formatTime(sample?.secondsIntoClip ?? 0)} telemetry position` : "Telemetry unavailable"}</span></div>
  </article>;
}

function VideoSessionsApp({ user }: { user: UserInfo }) {
  const [sessions, setSessions] = useState<RecordedSession[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    getRecordedSessions().then(setSessions).catch((reason) => setError(reason instanceof Error ? reason.message : "Could not load recorded sessions.")).finally(() => setLoading(false));
  }, []);

  return <AppShell user={user} title="Video Sessions" subtitle="Privacy-safe recorded footage and synchronized telemetry" active="Video Sessions" navItems={primaryNavItems} assistantZones={sessions.map((session) => ({ id: session.zoneId, name: session.zoneName }))}>
    <div className={styles.pageIntro}><div><span className={styles.kicker}>RECORDED COVERAGE · 6 ZONES</span><h2>Review processed session footage</h2><p>Each player uses face-blurred pipeline output. Telemetry advances from the video element’s current time and does not connect to live WebSocket data.</p></div><span className={styles.modeBadge}>PRIVACY MODE ACTIVE</span></div>
    {loading ? <Spinner label="Loading recorded sessions" /> : error ? <Card className={styles.errorState}><strong>Recorded sessions are unavailable</strong><span>{error}</span></Card> : <section className={styles.sessionGrid}>{sessions.map((session) => <RecordedSessionCard key={session.id} session={session} />)}</section>}
  </AppShell>;
}

export default function Page() {
  const [user, setUser] = useState<UserInfo>();
  useEffect(() => {
    const session = readSession();
    if (!session || session.user.role !== "ADMIN") {
      clearSession();
      window.location.replace("/console/login");
      return;
    }
    setUser(session.user);
  }, []);
  if (!user) return <main className={styles.loadingPage}>Checking command access</main>;
  return <VideoSessionsApp user={user} />;
}
