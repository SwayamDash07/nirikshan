"use client";

import { useEffect, useState } from "react";
import AppShell, { primaryNavItems } from "../../components/AppShell";
import { Card } from "../../components/ui";
import { clearSession, readSession, type UserInfo } from "../../lib/auth";
import styles from "./scenarios.module.css";

function LocalScenarioInfo({ user }: { user: UserInfo }) {
  return <AppShell user={user} title="Scenario Simulator" subtitle="Scenario generation and replay run on the local machine" active="Simulator" navItems={primaryNavItems}>
    <section className={styles.trainingBanner}><strong>LOCAL-ONLY SCENARIO REPLAY</strong><p>The deployed backend never launches the scenario generator or replay subprocess. Generate fixtures and POST them locally to Spring Boot or Render.</p></section>
    <Card className={styles.note}><strong>Replay against Render</strong><p><code>python cv-pipeline/replay_scenarios.py persistent --zone-id 1 --output cv-pipeline/outputs/demo-persistent.json --target-url https://&lt;render-backend-url&gt;/api/risk-events --speed 20</code></p><p>Use <code>--target-url http://localhost:8080/api/risk-events</code> for local Spring Boot. The controls that would imply backend subprocess execution are intentionally disabled.</p></Card>
  </AppShell>;
}

export default function Page() {
  const [user, setUser] = useState<UserInfo>();
  useEffect(() => {
    const session = readSession();
    if (!session || session.user.role !== "ADMIN") { clearSession(); window.location.replace("/console/login"); return; }
    setUser(session.user);
  }, []);
  if (!user) return <main className={styles.loading}>Checking administrator access</main>;
  return <LocalScenarioInfo user={user} />;
}
