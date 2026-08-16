"use client";

import { useEffect, useState } from "react";
import AppShell, { NavItem } from "../../components/AppShell";
import { Button } from "../../components/ui";
import { clearSession, readSession, type UserInfo } from "../../lib/auth";
import ResponseActions from "../ResponseActions";
import styles from "../console.module.css";
import managementStyles from "../management.module.css";

const navItems: NavItem[] = [{ label: "Dashboard", href: "/console", icon: "grid" }, { label: "Administration", href: "/console/admin", icon: "users" }, { label: "Video ingestion", href: "/admin", icon: "upload" }, { label: "Simulator", href: "/admin/scenarios", icon: "activity" }, { label: "Security", href: "/alerts/security", icon: "lock" }];

export default function Page() {
  const [user, setUser] = useState<UserInfo>();

  useEffect(() => {
    const session = readSession();
    if (!session || session.user.role !== "ADMIN") { clearSession(); window.location.replace("/console/login"); return; }
    if (session.user.mustChangePassword) { window.location.replace("/alerts/security"); return; }
    setUser(session.user);
  }, []);

  if (!user) return <main className={styles.loadingPage}>Checking access</main>;
  return <AppShell user={user} title="Administration" subtitle="Manage operational access and coordinate response" active="Administration" navItems={navItems}>
    <ResponseActions />
    <section className={managementStyles.adminLinks}>
      <div><span className={managementStyles.kicker}>STAFF ACCESS</span><h2>Security personnel</h2><p>Create accounts, review available staff, and send instructions.</p></div>
      <div className={managementStyles.adminLinkList}>
        <a className={managementStyles.staffLink} href="/console/admin/staff"><span><strong>Manage staff</strong><small>Create and remove security accounts and review the staff list.</small></span><Button variant="secondary" size="sm">Open page</Button></a>
        <a className={managementStyles.staffLink} href="/console/admin/actions"><span><strong>Send instructions</strong><small>Send a message to all security personnel, a zone team, or one person.</small></span><Button variant="secondary" size="sm">Open page</Button></a>
      </div>
    </section>
  </AppShell>;
}
