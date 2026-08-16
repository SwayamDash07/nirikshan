"use client";

import { useEffect, useState } from "react";
import AppShell, { NavItem } from "../../../components/AppShell";
import { Spinner } from "../../../components/ui";
import { api, clearSession, readSession, type UserInfo } from "../../../lib/auth";
import AdminManagement from "../../AdminManagement";
import styles from "../../console.module.css";

type Zone = { id: number; name: string };
type Venue = { id: number; name: string };
const navItems: NavItem[] = [{ label: "Dashboard", href: "/console", icon: "grid" }, { label: "Administration", href: "/console/admin", icon: "users" }, { label: "Video ingestion", href: "/admin", icon: "upload" }, { label: "Simulator", href: "/admin/scenarios", icon: "activity" }, { label: "Security", href: "/alerts/security", icon: "lock" }];

export default function Page() {
  const [user, setUser] = useState<UserInfo>();
  const [zones, setZones] = useState<Zone[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    const session = readSession();
    if (!session || session.user.role !== "ADMIN") { clearSession(); window.location.replace("/console/login"); return; }
    if (session.user.mustChangePassword) { window.location.replace("/alerts/security"); return; }
    setUser(session.user);
    (async () => {
      try {
        const venues = await api<Venue[]>("/api/venues");
        if (venues.length) setZones(await api<Zone[]>(`/api/venues/${venues[0].id}/zones`));
      } catch (reason) { setError(reason instanceof Error ? reason.message : "Could not load the instruction workspace."); }
      finally { setLoading(false); }
    })();
  }, []);

  if (!user) return <main className={styles.loadingPage}>Checking administration access</main>;
  return <AppShell user={user} title="Send instructions" subtitle="Coordinate a message to security personnel" active="Administration" navItems={navItems}>
    <a className={styles.backLink} href="/console/admin">Back to Administration</a>
    {loading ? <Spinner label="Loading instruction workspace" /> : error ? <div className={styles.errorState}><h2>Instruction workspace is unavailable</h2><p>{error}</p></div> : <AdminManagement zones={zones} view="messages" />}
  </AppShell>;
}
