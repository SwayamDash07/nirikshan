"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import AppShell, { NavItem } from "../../components/AppShell";
import CampusLocationPicker, { type CampusPoint, type CampusVenue, type VenueSelectionSource } from "../../components/CampusLocationPicker";
import { Button, Card, Field, Select, Spinner, Textarea } from "../../components/ui";
import { api, clearSession, readSession, type Session } from "../../lib/auth";
import styles from "../citizen.module.css";

type Zone = { id: number; name: string };
type Venue = CampusVenue;
const navItems: NavItem[] = [
  { label: "Safety alerts", href: "/alerts", icon: "bell" },
  { label: "Report issue", href: "/alerts/report", icon: "activity" },
  { label: "Account", href: "/alerts/security", icon: "settings" },
];

function ReportIssue({ session }: { session: Session }) {
  const [venues, setVenues] = useState<Venue[]>([]); const [venue, setVenue] = useState<Venue>(); const [zones, setZones] = useState<Zone[]>([]); const [location, setLocation] = useState<CampusPoint>(); const [zoneId, setZoneId] = useState<number>(); const [description, setDescription] = useState(""); const [loading, setLoading] = useState(true); const [sending, setSending] = useState(false); const [error, setError] = useState(""); const [notice, setNotice] = useState("");
  const load = useCallback(async () => { setLoading(true); setError(""); try { const availableVenues = venues.length ? venues : await api<Venue[]>("/api/venues"); if (!availableVenues.length) throw new Error("No venue is available yet."); if (!venues.length) setVenues(availableVenues); const savedId = Number(window.localStorage.getItem("nirikshan.selectedVenue")); const selected = availableVenues.find((item) => item.id === savedId) || venue || availableVenues[0]; const venueZones = await api<Zone[]>(`/api/venues/${selected.id}/zones`); setVenue(selected); setZones(venueZones); setZoneId((current) => current && venueZones.some((zone) => zone.id === current) ? current : venueZones[0]?.id); } catch (reason) { setError(reason instanceof Error ? reason.message : "Could not load reporting areas"); } finally { setLoading(false); } }, [venue, venues]);
  useEffect(() => { load(); }, []);
  const selectVenue = useCallback((next: CampusVenue, _source: VenueSelectionSource) => { setVenue(next); window.localStorage.setItem("nirikshan.selectedVenue", String(next.id)); }, []);
  useEffect(() => { if (venue && venues.length) load(); }, [venue?.id]);
  async function report(event: FormEvent) { event.preventDefault(); setSending(true); setError(""); setNotice(""); try { await api("/api/citizen-reports", { method: "POST", body: JSON.stringify({ zoneId, description }) }); setDescription(""); setNotice("Your report was sent to the safety team."); } catch (reason) { setError(reason instanceof Error ? reason.message : "Could not send report"); } finally { setSending(false); } }
  if (session.user.mustChangePassword) { window.location.replace("/alerts/security"); return <main className={styles.centerState}>Opening account security</main>; }
  return <AppShell user={session.user} title="Report an issue" subtitle={`${venue?.name || "Your campus"} safety reporting`} active="Report issue" navItems={navItems}>
    <div className={styles.reportIntro}><span className={styles.eyebrow}>DIRECT REPORTING</span><h2>Tell the safety team</h2><p>Send a quick report about a hazard, crowding, blocked route, or anything that could affect people nearby.</p></div>
    <CampusLocationPicker venues={venues} selectedVenue={venue} location={location} onLocationChange={setLocation} onSelect={selectVenue} />
    {error && <div className={styles.errorBanner} role="alert">{error}<button type="button" onClick={load}>Try again</button></div>}
    {loading ? <Spinner label="Loading reporting areas" /> : <Card className={styles.reportCard}><div className={styles.formColumn}><form onSubmit={report}><Field label="Nearest area"><Select value={zoneId || ""} onChange={(event) => setZoneId(Number(event.target.value))} required disabled={!zones.length}><option value="">{zones.length ? "Choose an area" : "No areas available"}</option>{zones.map((zone) => <option value={zone.id} key={zone.id}>{zone.name}</option>)}</Select></Field><Field label="What happened?"><Textarea value={description} onChange={(event) => setDescription(event.target.value)} rows={6} placeholder="Describe what you noticed" required /></Field>{notice && <p className={styles.notice} role="status">{notice}</p>}<Button size="lg" disabled={sending || !zoneId || !description.trim()}>{sending ? "Sending report" : "Send report"}</Button></form></div><div className={styles.reportAside}><span className={styles.reportAsideLabel}>WHEN TO REPORT</span><p>Use this form for hazards, crowding, blocked routes, or anything that could affect people nearby.</p><span className={styles.reportAsideLabel}>WHAT HAPPENS NEXT</span><p>The safety team reviews your report and coordinates the appropriate response.</p></div></Card>}
  </AppShell>;
}

export default function Page() {
  const [session, setSession] = useState<Session>();
  useEffect(() => { const next = readSession(); if (!next) { clearSession(); window.location.replace("/alerts"); return; } const customerPreview = next.user.role === "ADMIN" && new URLSearchParams(window.location.search).get("preview") === "customer"; if (next.user.role === "ADMIN" && !customerPreview) { window.location.replace("/console"); return; } if (next.user.role === "SECURITY") { window.location.replace("/security"); return; } setSession(next); }, []);
  if (!session) return <main className={styles.centerState}>Checking citizen access</main>;
  return <ReportIssue session={session} />;
}
