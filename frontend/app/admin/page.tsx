"use client";

import { useEffect, useState } from "react";
import AppShell, { primaryNavItems } from "../components/AppShell";
import { Card } from "../components/ui";
import { clearSession, readSession, type UserInfo } from "../lib/auth";
import styles from "./admin.module.css";

function LocalProcessingInfo({ user }: { user: UserInfo }) {
  return <AppShell user={user} title="Video ingestion" subtitle="Local GPU processing is managed outside the deployed backend" active="Video Ingestion" navItems={primaryNavItems}>
    <section className={styles.intro}><div><span className={styles.kicker}>LOCAL GPU PIPELINE</span><h1>Video processing runs on the GPU machine</h1><p>Render is an event-only Spring Boot backend. It does not accept footage for inference, launch Python, or run YOLO/SAHI.</p></div></section>
    <Card className={styles.simulationNote}><div><strong>Run the six-zone worker locally</strong><p>Generate a zone manifest on the RTX 4050 machine, then start one independent YOLO process per configured zone. Workers apply privacy-safe CV locally and POST risk events to the selected backend.</p><p><code>python cv-pipeline/run_local_cv_workers.py --manifest cv-pipeline/outputs/live/zones.json --target-url https://&lt;render-backend-url&gt;/api/risk-events --workers 6</code></p><p>For a scenario demo, generate and replay locally with <code>replay_scenarios.py --target-url</code>. This page intentionally has no upload or cloud-inference controls.</p></div></Card>
  </AppShell>;
}

export default function Page() {
  const [user, setUser] = useState<UserInfo>();
  useEffect(() => {
    const session = readSession();
    if (!session || session.user.role !== "ADMIN") { clearSession(); window.location.replace("/console/login"); return; }
    setUser(session.user);
  }, []);
  if (!user) return <main className={styles.state}>Checking access</main>;
  return <LocalProcessingInfo user={user} />;
}
