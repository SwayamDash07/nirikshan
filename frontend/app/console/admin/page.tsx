"use client";

import { useEffect, useState } from "react";
import AppShell, { NavItem } from "../../components/AppShell";
import { Button, Spinner } from "../../components/ui";
import { api, clearSession, readSession, type UserInfo } from "../../lib/auth";
import AdminManagement from "../AdminManagement";
import styles from "../console.module.css";
import managementStyles from "../management.module.css";

type Zone = { id: number; name: string };
type Venue = { id: number; name: string };
const navItems: NavItem[] = [{ label: "Dashboard", href: "/console", icon: "grid" }, { label: "Administration", href: "/console/admin", icon: "users" }, { label: "Video ingestion", href: "/admin", icon: "upload" }, { label: "Security", href: "/alerts/security", icon: "lock" }];

export default function Page() { const [user, setUser] = useState<UserInfo>(); const [zones, setZones] = useState<Zone[]>([]); const [loading, setLoading] = useState(true); const [error, setError] = useState(""); useEffect(() => { const session = readSession(); if (!session || session.user.role !== "ADMIN") { clearSession(); window.location.replace("/console/login"); return; } if (session.user.mustChangePassword) { window.location.replace("/alerts/security"); return; } setUser(session.user); (async () => { try { const venues = await api<Venue[]>("/api/venues"); if (venues.length) setZones(await api<Zone[]>(`/api/venues/${venues[0].id}/zones`)); } catch (reason) { setError(reason instanceof Error ? reason.message : "Could not load administration workspace."); } finally { setLoading(false); } })(); }, []); if (!user) return <main className={styles.loadingPage}>Checking access</main>; return <AppShell user={user} title="Administration" subtitle="Manage operational access and coordinate response" active="Administration" navItems={navItems}>{loading ? <Spinner label="Loading administration workspace" /> : error ? <div className={styles.errorState}><h2>Administration data is unavailable</h2><p>{error}</p></div> : <><section className={managementStyles.adminLinks}><div><span className={managementStyles.kicker}>WORKSPACE AREAS</span><h2>Choose an administration area</h2><p>Keep staff credentials separate from day to day coordination.</p></div><a className={managementStyles.staffLink} href="/console/admin/staff"><span><strong>Staff access</strong><small>Create accounts, assign zones, and deactivate access.</small></span><Button variant="secondary" size="sm">Open page</Button></a></section><AdminManagement zones={zones} /></>}</AppShell>; }
