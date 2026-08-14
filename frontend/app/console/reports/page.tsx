"use client";

import { useEffect, useState } from "react";
import AppShell, { NavItem } from "../../components/AppShell";
import { Card, Spinner } from "../../components/ui";
import { api, clearSession, readSession, type UserInfo } from "../../lib/auth";
import styles from "../console.module.css";

type Report = { id: number; zoneId: number; zoneName: string; description: string; timestamp: string; status: string };
const navItems: NavItem[] = [{ label: "Dashboard", href: "/console", icon: "grid" }, { label: "Citizen reports", href: "/console/reports", icon: "activity" }, { label: "Administration", href: "/console/admin", icon: "users" }, { label: "Video ingestion", href: "/admin", icon: "upload" }, { label: "Security", href: "/alerts/security", icon: "lock" }];
function age(value: string) { const seconds = Math.max(0, Math.round((Date.now() - new Date(value).valueOf()) / 1000)); return seconds < 60 ? "Just now" : seconds < 3600 ? `${Math.floor(seconds / 60)} min ago` : `${Math.floor(seconds / 3600)} hr ago`; }

function Reports({ user }: { user: UserInfo }) {
  const [reports, setReports] = useState<Report[]>([]); const [loading, setLoading] = useState(true); const [error, setError] = useState("");
  useEffect(() => { api<Report[]>("/api/citizen-reports").then(setReports).catch((reason) => setError(reason instanceof Error ? reason.message : "Could not load citizen reports")).finally(() => setLoading(false)); }, []);
  return <AppShell user={user} title="Citizen reports" subtitle="Reports submitted by campus members, grouped by location" active="Citizen reports" navItems={navItems}>{loading ? <Spinner label="Loading citizen reports" /> : error ? <div className={styles.errorState}><span className={styles.kicker}>REPORTS UNAVAILABLE</span><h2>Could not load citizen reports</h2><p>{error}</p></div> : <section className={styles.alertSection}><div className={styles.sectionHeading}><div><span className={styles.kicker}>RESPONSE INBOX</span><h2>Campus member reports</h2></div><span>{reports.length} received</span></div><div className={styles.fullAlertList}>{reports.length ? reports.map((report) => <article className={styles.fullAlert} key={report.id}><span className={styles.alertRail} /><div className={styles.fullAlertBody}><div><strong>{report.zoneName}</strong><span className={styles.reportStatus}>{report.status}</span></div><p>{report.description}</p><small>{age(report.timestamp)}</small></div></article>) : <Card className={styles.tableEmpty}>No citizen reports have been submitted.</Card>}</div></section>}</AppShell>;
}

export default function Page() { const [user, setUser] = useState<UserInfo>(); useEffect(() => { const session = readSession(); if (!session || session.user.role !== "ADMIN") { clearSession(); window.location.replace("/console/login"); return; } setUser(session.user); }, []); if (!user) return <main className={styles.loadingPage}>Checking command access</main>; return <Reports user={user} />; }
