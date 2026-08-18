"use client";

import dynamic from "next/dynamic";
import { useEffect, useState } from "react";
import AppShell, { primaryNavItems } from "../../components/AppShell";
import { Card } from "../../components/ui";
import { clearSession, readSession, type UserInfo } from "../../lib/auth";
import styles from "../console.module.css";

const Campus3DMap = dynamic(() => import("../Campus3DMap"), {
  ssr: false,
  loading: () => <div className={styles.mapLoading}>Loading 3D route model</div>,
});

function ThreeDMap({ user }: { user: UserInfo }) {
  return (
    <AppShell
      user={user}
      title="3D map"
      subtitle="Coordinate-aligned Campus 25 route with evidence status"
      active="3D Model"
      navItems={primaryNavItems}
    >
      <Card className={styles.mapCard}>
        <div className={styles.cardHeader}>
          <div>
            <span className={styles.kicker}>CAMPUS 25 MODEL</span>
            <h2>3D route corridor</h2>
            <p>Follow the coordinate-backed path from Main Gate through the campus to Main Gate Exit. Open Reference/debug to audit photo and PDF evidence per area.</p>
          </div>
          <span className={styles.liveLabel}>MAP-ALIGNED</span>
        </div>
        <div className={styles.mapWrap}>
          <Campus3DMap />
        </div>
      </Card>
    </AppShell>
  );
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
    if (session.user.mustChangePassword) {
      window.location.replace("/alerts/security");
      return;
    }
    setUser(session.user);
  }, []);

  if (!user) return <main className={styles.loadingPage}>Checking access</main>;
  return <ThreeDMap user={user} />;
}
