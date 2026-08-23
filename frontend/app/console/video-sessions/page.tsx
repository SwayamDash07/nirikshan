"use client";

import { useEffect, useState } from "react";
import AppShell, { primaryNavItems } from "../../components/AppShell";
import { Card, Spinner } from "../../components/ui";
import { clearSession, readSession, type UserInfo } from "../../lib/auth";
import { getRecordedSessions, type RecordedSession } from "./actions";
import styles from "./video-sessions.module.css";

function RecordedSessionCard({ session }: { session: RecordedSession }) {
  const [videoFailed, setVideoFailed] = useState(false);

  return <article className={styles.sessionCard}>
    <div className={styles.sessionHeader}>
      <div><span className={styles.kicker}>ZONE {String(session.zoneId).padStart(2, "0")}</span><h2>{session.zoneName}</h2><p>{session.cameraLabel}</p></div>
      <span className={styles.privacyBadge}>Recorded Session · Privacy Mode Active</span>
    </div>
    <div className={styles.videoFrame}>
      <video className={styles.video} src={session.videoUrl || undefined} autoPlay loop muted playsInline controls preload="metadata" onError={() => setVideoFailed(true)} aria-label={`Recorded privacy-safe footage for ${session.zoneName}`} />
      {(!session.videoAvailable || videoFailed) && <div className={styles.videoUnavailable}><strong>Recorded video unavailable</strong><span>Check that the privacy-safe recording is available from the backend.</span></div>}
    </div>
    <div className={styles.sessionFacts} aria-label={`${session.zoneName} measured area`}>
      <div className={styles.factCard}><span>Measured area</span><strong>{session.measuredAreaSqMeters} m²</strong><small>Configured visible area for this zone</small></div>
    </div>
  </article>;
}

function VideoSessionsApp({ user }: { user: UserInfo }) {
  const [sessions, setSessions] = useState<RecordedSession[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    getRecordedSessions().then(setSessions).catch((reason) => setError(reason instanceof Error ? reason.message : "Could not load recorded sessions.")).finally(() => setLoading(false));
  }, []);

  return <AppShell user={user} title="Video Sessions" subtitle="Privacy-safe recorded footage and fixed zone calibration" active="Video Sessions" navItems={primaryNavItems} assistantZones={sessions.map((session) => ({ id: session.zoneId, name: session.zoneName }))}>
    <div className={styles.pageIntro}><div><span className={styles.kicker}>RECORDED COVERAGE · 6 ZONES</span><h2>Review processed session footage</h2><p>Each player uses face-blurred pipeline output. Changing replay metrics are hidden because these sessions are recorded, not live.</p><div className={styles.formulaNote}><strong>Evaluation formula · all zones</strong><span>Risk score = 0.45 × density + 0.30 × density increase + 0.25 × movement speed drop.</span></div></div><span className={styles.modeBadge}>PRIVACY MODE ACTIVE</span></div>
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
