"use client";

import { useEffect, useState } from "react";
import AppShell, { primaryNavItems } from "../../../components/AppShell";
import { clearSession, readSession, type UserInfo } from "../../../lib/auth";
import CheckInManagement from "../../CheckInManagement";
import styles from "../../console.module.css";

const navItems = primaryNavItems;

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

  return <AppShell user={user} title="Staff check-ins" subtitle="Trigger confirmations and monitor response status" active="Administration" navItems={navItems}>
    <a className={styles.backLink} href="/console/admin">Back to Administration</a>
    <CheckInManagement />
  </AppShell>;
}
