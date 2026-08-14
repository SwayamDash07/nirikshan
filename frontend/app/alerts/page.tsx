"use client";

import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import dynamic from "next/dynamic";
import AppShell, { NavItem } from "../components/AppShell";
import { Button, Card, Field, Input, Spinner } from "../components/ui";
import { api, clearSession, readSession, saveSession, type Session } from "../lib/auth";
import styles from "./citizen.module.css";

const CitizenMiniMap = dynamic(() => import("./CitizenMiniMap"), { ssr: false, loading: () => <div className={styles.mapLoading}>Loading safety map</div> });
type RiskLevel = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
type Zone = { id: number; name: string; latitude: number; longitude: number; currentDensity: number; currentRiskLevel: RiskLevel; lastUpdated: string };
type Venue = { id: number; name: string };
type Alert = { id: number; zoneId: number; zoneName?: string; timestamp: string; message: string; severity: RiskLevel; resolved: boolean };
type Point = { lat: number; lng: number };
const labels: Record<RiskLevel, string> = { LOW: "Normal", MEDIUM: "Watch", HIGH: "High", CRITICAL: "Critical" };
const navItems: NavItem[] = [
  { label: "Safety alerts", href: "/alerts", icon: "bell" },
  { label: "Report issue", href: "/alerts/report", icon: "activity" },
  { label: "Account", href: "/alerts/security", icon: "settings" },
];

function ago(value: string) { const seconds = Math.max(0, Math.round((Date.now() - new Date(value).valueOf()) / 1000)); return seconds < 60 ? "Just now" : seconds < 3600 ? `${Math.floor(seconds / 60)} min ago` : `${Math.floor(seconds / 3600)} hr ago`; }
function distanceBetween(a: Point, b: Point) { const radius = 6371000; const radians = (value: number) => value * Math.PI / 180; const haversine = Math.sin(radians(b.lat - a.lat) / 2) ** 2 + Math.cos(radians(a.lat)) * Math.cos(radians(b.lat)) * Math.sin(radians(b.lng - a.lng) / 2) ** 2; return Math.round(radius * 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine))); }
function point(zones: Zone[], id: number): Point { const zone = zones.find((item) => item.id === id); return zone ? { lat: zone.latitude, lng: zone.longitude } : { lat: 20.3641, lng: 85.8163 }; }

function Auth({ done }: { done: (session: Session) => void }) {
  const [signup, setSignup] = useState(false); const [name, setName] = useState(""); const [email, setEmail] = useState(""); const [password, setPassword] = useState(""); const [error, setError] = useState(""); const [working, setWorking] = useState(false);
  async function submit(event: FormEvent) { event.preventDefault(); setWorking(true); setError(""); try { const session = await api<Session>(`/api/auth/${signup ? "signup" : "login"}`, { method: "POST", body: JSON.stringify(signup ? { name, email, password } : { email, password }) }); saveSession(session); done(session); } catch (reason) { setError(reason instanceof Error ? reason.message : "Could not sign in"); } finally { setWorking(false); } }
  return <main className={styles.authPage}><section className={styles.authStory}><span className={styles.authBrand}>Nirikshan</span><div><span className={styles.eyebrow}>CAMPUS SAFETY NETWORK</span><h1>Know what is happening around you.</h1><p>Clear updates from the safety team, organized around your location and the places you use every day.</p></div><div className={styles.storyPoints}><span><b>01</b>Active conditions in one view</span><span><b>02</b>Short, clear safety instructions</span><span><b>03</b>Direct reporting to the safety team</span></div></section><section className={styles.authPanel}><div className={styles.authPanelTop}><span>Citizen access</span><span className={styles.authStatus}>Secure connection</span></div><form className={styles.authCard} onSubmit={submit}><div className={styles.eyebrow}>NIRIKSHAN ALERTS</div><h2>{signup ? "Create your account" : "Welcome back"}</h2><p className={styles.authIntro}>{signup ? "Create a citizen account to receive campus updates." : "Sign in to see current campus safety information."}</p>{signup && <Field label="Full name"><Input value={name} onChange={(event) => setName(event.target.value)} autoComplete="name" required /></Field>}<Field label="Email"><Input value={email} onChange={(event) => setEmail(event.target.value)} type="email" autoComplete="email" required /></Field><Field label="Password" hint="Use at least 8 characters"><Input value={password} onChange={(event) => setPassword(event.target.value)} type="password" autoComplete={signup ? "new-password" : "current-password"} minLength={8} required /></Field>{error && <div className={styles.error} role="alert">{error}</div>}<Button size="lg" className={styles.fullButton} disabled={working}>{working ? "Please wait" : signup ? "Create citizen account" : "Sign in"}</Button><button className={styles.textButton} type="button" onClick={() => { setSignup(!signup); setError(""); }}>{signup ? "Already registered? Sign in" : "New here? Create a citizen account"}</button><small className={styles.formNote}>Security and administrator accounts are issued by the safety team.</small></form></section></main>;
}

function Citizen({ session }: { session: Session }) {
  const [venue, setVenue] = useState<Venue>(); const [zones, setZones] = useState<Zone[]>([]); const [alerts, setAlerts] = useState<Alert[]>([]); const [location, setLocation] = useState<Point>(); const [error, setError] = useState(""); const [loading, setLoading] = useState(true);
  const load = useCallback(async () => { setLoading(true); setError(""); try { const venues = await api<Venue[]>("/api/venues"); if (!venues.length) throw new Error("No venue is available yet."); const [venueZones, activeAlerts] = await Promise.all([api<Zone[]>(`/api/venues/${venues[0].id}/zones`), api<Alert[]>("/api/alerts?active=true")]); setVenue(venues[0]); setZones(venueZones); setAlerts(activeAlerts); } catch (reason) { setError(reason instanceof Error ? reason.message : "Could not load safety alerts"); } finally { setLoading(false); } }, []);
  useEffect(() => { load(); navigator.geolocation?.getCurrentPosition((position) => setLocation({ lat: position.coords.latitude, lng: position.coords.longitude })); }, [load]);
  const sorted = useMemo(() => [...alerts].sort((a, b) => location ? distanceBetween(location, point(zones, a.zoneId)) - distanceBetween(location, point(zones, b.zoneId)) : +new Date(b.timestamp) - +new Date(a.timestamp)), [alerts, location, zones]);
  if (session.user.mustChangePassword) { window.location.replace("/alerts/security"); return <main className={styles.centerState}>Opening account security</main>; }
  return <AppShell user={session.user} title="Safety alerts" subtitle={`${venue?.name || "Your campus"} and current safety information`} active="Safety alerts" navItems={navItems}>
    <div className={styles.citizenIntro}><div><span className={styles.eyebrow}>YOUR SAFETY BRIEFING</span><h2>Good to see you, {session.user.name.split(" ")[0]}.</h2><p>Here is the latest picture from your campus safety network.</p></div><button className={styles.refreshButton} type="button" onClick={load} disabled={loading}>{loading ? "Refreshing" : "Refresh data"}</button></div>
    {error && <div className={styles.errorBanner} role="alert">{error}<button type="button" onClick={load}>Try again</button></div>}
    {loading ? <Spinner label="Loading campus safety data" /> : <>
      <section className={styles.citizenGrid}><Card className={styles.mapCard}><div className={styles.cardHeader}><div><span className={styles.kicker}>LOCATION CONTEXT</span><h2>Campus map</h2><p>Nearby areas and current conditions.</p></div><span className={styles.locationState}><i />{location ? "Location on" : "Location off"}</span></div><CitizenMiniMap zones={zones} alerts={alerts} location={location} /></Card><Card className={styles.briefCard}><span className={styles.kicker}>LIVE BRIEFING</span><h2>{sorted.length ? `${sorted.length} active updates` : "All clear for now"}</h2><p>{sorted.length ? "Review the highest priority updates first." : "No active conditions have been published for your campus."}</p><div className={styles.briefStat}><span>Nearest update</span><strong>{sorted[0] ? sorted[0].zoneName || `Zone ${sorted[0].zoneId}` : "No active update"}</strong></div><a href="#alerts" className={styles.primaryLink}>Review safety alerts <span>→</span></a></Card></section>
      <section id="alerts" className={styles.alertSection}><div className={styles.sectionHeading}><div><span className={styles.kicker}>SAFETY ALERTS</span><h2>Updates near you</h2></div><span>{sorted.length} active</span></div>{sorted.length ? <div className={styles.alertGrid}>{sorted.map((alert) => <article className={styles.alertCard} key={alert.id}><div className={styles.alertTop}><span className={`${styles.severity} ${styles[`severity${alert.severity}`]}`}>{labels[alert.severity]}</span>{location && <span className={styles.distance}>{distanceBetween(location, point(zones, alert.zoneId))}m away</span>}</div><h3>{alert.zoneName || `Zone ${alert.zoneId}`}</h3><p>{alert.message}</p><small>{ago(alert.timestamp)}</small></article>)}</div> : <div className={styles.emptyState}>No active alerts. The safety team has not published any current updates.</div>}</section>
    </>}
  </AppShell>;
}

export default function Page() {
  const [session, setSession] = useState<Session | null>(null);
  useEffect(() => { const next = readSession(); if (!next) return; const customerPreview = next.user.role === "ADMIN" && new URLSearchParams(window.location.search).get("preview") === "customer"; if (next.user.role === "ADMIN" && !customerPreview) { window.location.replace("/console"); return; } if (next.user.role === "SECURITY") { window.location.replace("/security"); return; } setSession(next); }, []);
  if (!session) return <Auth done={setSession} />;
  return <Citizen session={session} />;
}
