"use client";

import { useEffect, useState } from "react";
import AppShell, { NavItem } from "../../components/AppShell";
import { Card, Spinner } from "../../components/ui";
import { api, clearSession, readSession, type UserInfo } from "../../lib/auth";
import styles from "../security.module.css";

type Report = { id: number; zoneId: number; zoneName: string; description: string; timestamp: string; status: string };
const navItems: NavItem[] = [{ label: "Assigned zone", href: "/security", icon: "grid" }, { label: "Citizen reports", href: "/security/reports", icon: "activity" }, { label: "Instructions", href: "/security#instructions", icon: "activity" }, { label: "Security", href: "/alerts/security", icon: "lock" }];
function age(value: string) { const seconds = Math.max(0, Math.round((Date.now() - new Date(value).valueOf()) / 1000)); return seconds < 60 ? "Just now" : seconds < 3600 ? `${Math.floor(seconds / 60)} min ago` : `${Math.floor(seconds / 3600)} hr ago`; }

function Reports({ shellUser, preview }: { shellUser: UserInfo; preview: boolean }) {
  const [reports, setReports] = useState<Report[]>([]); const [loading, setLoading] = useState(true); const [error, setError] = useState("");
  useEffect(() => { api<Report[]>(preview ? "/api/citizen-reports" : "/api/security/reports").then(setReports).catch((reason) => setError(reason instanceof Error ? reason.message : "Could not load assigned-zone reports")).finally(() => setLoading(false)); }, [preview]);
  return <AppShell user={shellUser} previewRole={preview ? "SECURITY" : undefined} title="Citizen reports" subtitle="Reports submitted near your assigned zone" active="Citizen reports" navItems={navItems}>{loading ? <Spinner label="Loading assigned-zone reports" /> : error ? <div className={styles.error} role="alert">{error}</div> : <section className={styles.reportPage}><div className={styles.cardHeader}><div><span className={styles.kicker}>ASSIGNED ZONE INBOX</span><h2>Campus member reports</h2><p>These reports are routed here because they belong to your assigned zone.</p></div><span className={styles.count}>{reports.length}</span></div>{preview && <div className={styles.previewBanner}>Preview mode is active. Reports are loaded with administrator permissions for testing.</div>}<div className={styles.reportList}>{reports.length ? reports.map((report) => <article className={styles.report} key={report.id}><div><strong>{report.zoneName}</strong><span>{report.status}</span></div><p>{report.description}</p><small>{age(report.timestamp)}</small></article>) : <Card className={styles.empty}><strong>No reports for this zone</strong><span>New campus member reports will appear here.</span></Card>}</div></section>}</AppShell>;
}

export default function Page() { const [access, setAccess] = useState<{ user: UserInfo; preview: boolean }>(); useEffect(() => { const session = readSession(); const preview = new URLSearchParams(window.location.search).get("preview") === "security"; if (!session) { clearSession(); window.location.replace("/alerts"); return; } if (session.user.role === "ADMIN" && preview) { setAccess({ user: session.user, preview: true }); return; } if (session.user.role !== "SECURITY") { window.location.replace(session.user.role === "ADMIN" ? "/console" : "/alerts"); return; } setAccess({ user: session.user, preview: false }); }, []); if (!access) return <main className={styles.state}>Checking access</main>; return <Reports shellUser={access.user} preview={access.preview} />; }
